# 云票务优惠券与评论系统设计

## 1. 目标与范围

在现有 Cloud Ticketing 多服务票务系统中增加优惠券和活动评论能力，参考 `D:\development\project\tjxt` 中的 promotion、remark 和互动问答设计，但按票务场景重新建模。

本次第一版范围：

- 优惠券支持满减、折扣、无门槛减免。
- 优惠券支持全场、指定活动、指定场次三种适用范围。
- 一笔订单最多使用一张优惠券，不做优惠券叠加。
- 用户领取优惠券，订单创建时冻结，支付成功时核销，取消/过期时释放，退款成功时恢复。
- 排队销售（`QUEUED`）第一版不支持优惠券；优惠券只支持直接下单（`DIRECT`）。
- 活动支持评论、一级回复、点赞、管理员隐藏与恢复。
- 只有在该活动下存在已支付订单的用户才能发表评论；未购买用户可以查看和点赞。
- 不改变现有 JWT、库存锁、订单幂等、支付模拟和既有 API 的行为。

## 2. 参考设计与适配原则

参考天机学堂的以下实现思路：

- 使用策略模式隔离不同优惠券折扣算法。
- 使用 Redis Lua 将优惠券库存扣减、用户限领校验和重复领取判断合并为原子操作。
- 使用用户券状态记录区分未使用、冻结、已使用和已过期。
- 使用 Redis Set 处理点赞去重，使用消息异步回写点赞数量。
- 使用父评论/回复关系和隐藏状态支持互动内容管理。

不直接复制天机学堂的课程、分类和用户表结构；活动和场次分别使用现有 `activity_id`、`session_id`，用户标识沿用现有 JWT 下发的用户 ID。

## 3. 服务边界

### 3.1 promotion-service

负责优惠券定义、发布、适用范围、用户领取、优惠券冻结/核销/释放/恢复和优惠计算。拥有独立 promotion 数据库，通过 Gateway 对外暴露用户和管理员 API，通过内部 HTTP API 与 order-service 协作。

### 3.2 comment-service

负责活动评论、一级回复、点赞、内容隐藏/恢复和评论分页。拥有独立 comment 数据库。评论创建前调用 order-service 的内部资格接口，判断用户是否有目标活动的已支付订单。

### 3.3 order-service

继续拥有订单金额和订单状态，是订单金额快照的最终来源。创建订单时调用 promotion-service 获取优惠报价并冻结优惠券；保存原价、优惠金额、实付金额和优惠券冻结记录。现有 Outbox + Kafka 继续负责支付成功、取消、过期和退款事件。

### 3.4 activity-service

继续负责活动、活动图片、场次和场馆。内部场次查询需要补充返回 `activityId`，让 order-service 在创建订单时保存活动 ID 快照，避免 comment-service 为资格判断反查场次归属。

### 3.5 gateway-service 与 common-events

Gateway 增加 promotion-service 和 comment-service 路由；common-events 增加优惠券生命周期事件和评论点赞数量变更事件常量。服务间调用沿用现有内部服务 Token。

## 4. 优惠券订单流程

### 4.1 直接下单

```text
Web 提交 sessionId、座位/数量、couponId、idempotencyKey
  -> order-service 查询场次，得到 activityId 和单价
  -> order-service 计算原价
  -> promotion-service quote：校验用户券、适用范围、有效期和门槛，并创建 HELD 冻结
  -> order-service 写入订单和金额快照
  -> inventory-service 锁定座位或通票
  -> 订单进入 PENDING
```

优惠券 quote 必须以 `orderId` 或预生成的订单 ID 作为幂等键。若订单写入或库存锁定失败，order-service 调用 release；release 失败时记录补偿任务，不能静默丢失冻结状态。

### 4.2 支付与状态变化

```text
PENDING + PaymentSucceeded -> order-service 标记 PAID -> 事件 -> promotion-service CONSUMED
PENDING + cancel/expiry       -> 订单状态变化 -> 事件 -> promotion-service RELEASED
PAID + refund approved        -> 订单状态 REFUNDED -> 事件 -> promotion-service RESTORED/EXPIRED
```

所有消费、释放和恢复操作以 `reservationId + eventId` 幂等。恢复时重新检查优惠券有效期，仍在有效期内则回到 `UNUSED`，已过期则进入 `EXPIRED`。

### 4.3 金额规则

- `originalAmountMinor = unitPriceMinor * quantity` 或 `unitPriceMinor * seatCount`。
- `discountAmountMinor` 由 promotion-service 计算并返回，不能由前端传入。
- `amountMinor = max(0, originalAmountMinor - discountAmountMinor)`。
- 折扣金额不能大于原价。
- 订单支付和支付二维码只读取订单中已保存的 `amountMinor`。
- 重放相同幂等键时返回原订单，不重复冻结优惠券。

## 5. 优惠券数据模型

### 5.1 `promotion_coupon`

保存优惠券定义：

- `id`、`name`、`discount_type`
- `threshold_amount_minor`、`discount_value`、`max_discount_minor`
- `total_count`、`issued_count`、`used_count`
- `user_limit`
- `issue_begin_at`、`issue_end_at`
- `term_begin_at`、`term_end_at`、`term_days`
- `status`：`DRAFT`、`PUBLISHED`、`OFFLINE`、`ENDED`
- `created_by`、`created_at`、`updated_at`

### 5.2 `promotion_coupon_scope`

- `coupon_id`
- `resource_type`：`ALL`、`ACTIVITY`、`SESSION`
- `resource_id`
- 唯一键：`coupon_id + resource_type + resource_id`

空范围表示全场券；非空范围必须至少命中当前活动或当前场次之一。

### 5.3 `promotion_user_coupon`

- `id`、`coupon_id`、`user_id`
- `status`：`UNUSED`、`HELD`、`USED`、`EXPIRED`
- `term_begin_at`、`term_end_at`
- `held_order_id`、`held_at`、`used_at`
- `created_at`、`updated_at`
- 索引：`user_id + status + term_end_at`、`coupon_id + user_id`

### 5.4 `promotion_coupon_reservation`

- `reservation_id`
- `order_id`，唯一
- `user_coupon_id`
- `user_id`
- `original_amount_minor`、`discount_amount_minor`、`payable_amount_minor`
- `status`：`HELD`、`CONSUMED`、`RELEASED`、`RESTORED`、`EXPIRED`
- `created_at`、`updated_at`
- 唯一键：`order_id`、`user_coupon_id + status=HELD` 的业务约束

## 6. 优惠券策略

promotion-service 使用 `DiscountStrategy` 注册表：

- `PriceDiscount`：满 X 减 Y。
- `RateDiscount`：满 X 打折，按 `maxDiscountMinor` 封顶。
- `NoThresholdDiscount`：无门槛减免，但实付金额不能低于 0。

策略接口至少包含：

```java
boolean canUse(int originalAmountMinor, Coupon coupon);
int calculateDiscount(int originalAmountMinor, Coupon coupon);
String describe(Coupon coupon);
```

领取流程使用 Redis Lua 原子校验：优惠券状态、领取时间、总库存、用户领取数量和重复领取限制。Lua 成功后发送 Kafka 领取事件，由 promotion-service 消费者在数据库创建 `promotion_user_coupon`；数据库消费者必须以事件 ID 幂等。

## 7. 评论与点赞数据模型

### 7.1 `comment`

- `id`
- `activity_id`
- `user_id`
- `parent_id`，一级评论为 NULL，回复指向一级评论
- `content`
- `status`：`VISIBLE`、`HIDDEN`
- `like_count`
- `reply_count`
- `created_at`、`updated_at`
- 索引：`activity_id + status + created_at`、`parent_id + status`

第一版禁止多级回复：回复的 `parent_id` 必须指向一级评论；回复评论时如果传入的目标已经是回复，归并到其一级父评论。

### 7.2 `comment_like_record`

- `comment_id`
- `user_id`
- `created_at`
- 主键：`comment_id + user_id`

点赞写入 Redis Set 做快速去重，同时保留数据库记录作为持久化来源。Redis 中维护待同步点赞数量的 ZSet；定时任务批量发出 `CommentLikeCountChanged` 事件，comment-service 使用条件更新或版本号避免覆盖较新的数量。

## 8. API 契约

### 8.1 promotion-service 对外 API

```text
GET  /api/promotions/coupons/available
POST /api/promotions/coupons/{couponId}/claim
GET  /api/promotions/my-coupons?status=UNUSED&page=0&size=20
```

管理员 API：

```text
GET  /api/admin/promotions/coupons
POST /api/admin/promotions/coupons
PUT  /api/admin/promotions/coupons/{id}
POST /api/admin/promotions/coupons/{id}/publish
POST /api/admin/promotions/coupons/{id}/offline
PUT  /api/admin/promotions/coupons/{id}/scopes
```

内部 API：

```text
POST /api/internal/promotions/quote
POST /api/internal/promotions/{reservationId}/consume
POST /api/internal/promotions/{reservationId}/release
POST /api/internal/promotions/{reservationId}/restore
```

### 8.2 comment-service 对外 API

```text
GET    /api/activities/{activityId}/comments?page=0&size=20
POST   /api/activities/{activityId}/comments
POST   /api/comments/{id}/replies
POST   /api/comments/{id}/like
DELETE /api/comments/{id}/like
```

管理员 API：

```text
GET /api/admin/comments?activityId=&status=&page=0&size=20
PUT /api/admin/comments/{id}/hide
PUT /api/admin/comments/{id}/restore
```

内部 API：

```text
GET /api/internal/orders/users/{userId}/activities/{activityId}/paid
```

该接口只返回资格布尔值和必要的订单数量，不暴露订单隐私字段。

## 9. 权限与安全

新增权限：

```text
promotion:read
promotion:write
promotion:publish
comment:read
comment:moderate
comment:like
```

- 普通用户领取和查看自己的优惠券只需登录。
- 评论创建必须登录且通过已支付活动资格校验。
- 评论列表对普通用户只返回 `VISIBLE` 内容。
- 管理员隐藏/恢复评论需要 `comment:moderate`，并使用活动 Scope 校验。
- 优惠券管理、发布和范围修改需要对应 promotion 权限，并写入审计日志。
- 内部 API 继续使用 `X-Internal-Service-Token`，不允许从浏览器直接调用。
- 评论内容限制长度并拒绝空白内容；优惠券金额、库存、日期和限领数在 API 与领域层双重校验。

## 10. 前端交互

### 10.1 活动与评论

BookingView 活动详情区域增加评论分页。已支付用户显示输入框和发表评论按钮；未购买用户显示登录/购买提示但仍可点赞。点赞操作失败时恢复原状态，不修改评论正文。

### 10.2 订单与优惠券

BookingView 在 `DIRECT` 模式下加载当前用户可用券，创建订单时传递 `couponId`。订单创建成功后，PaymentView 和 OrdersView 读取订单的金额快照，不再次请求优惠计算。`QUEUED` 模式不渲染优惠券选择器。

### 10.3 个人中心与管理端

ProfileView 增加“我的优惠券”分页；AdminLayout 增加优惠券管理和评论审核模块。现有 `data-testid` 保持不变，新控件增加稳定测试标识。

## 11. 错误处理与补偿

- promotion quote 失败：订单不创建，前端显示具体业务错误。
- 库存锁定失败：释放优惠券冻结并返回订单创建失败。
- 订单插入成功但发布事件失败：Outbox 重试，不重复扣券。
- consume/release/restore 重复调用：根据 reservation 状态返回幂等成功或当前状态。
- 定时任务扫描超过支付窗口的 `HELD` reservation，确认对应订单状态后释放异常冻结。
- 定时任务对 Redis 点赞计数和数据库 `like_count` 做差异修复。
- 所有补偿操作记录 traceId、reservationId/commentId 和失败原因。

## 12. 测试与验收

### 后端

- 折扣策略边界、封顶金额、实付不小于 0。
- Redis Lua 领取库存、重复领取和每人限领。
- 用户券冻结、核销、释放、恢复状态机和幂等。
- OrderRepository 在有券/无券、订单重放、库存失败时的金额和补偿。
- 支付成功、订单过期、取消、退款事件驱动的优惠券状态变化。
- 评论购买资格、一级回复、隐藏过滤、Scope 权限。
- 点赞/取消点赞幂等与点赞数量回写。
- 内部 API Token 校验和不泄露订单信息。

### 前端

- 优惠券列表和选择器只在 `DIRECT` 场次显示。
- 选择优惠券后展示原价、优惠金额和实付金额。
- 评论输入框只对有资格用户显示。
- 评论分页、回复、点赞和隐藏内容展示。
- 既有认证、选座、通票、订单和支付测试全部保持通过。

验收命令：

```powershell
mvn --% -q -Dsurefire.failIfNoSpecifiedTests=false test
cd web
npm run test -- --run
npm run build
```

## 13. 非目标

- 第一版不支持优惠券叠加和复杂的优惠组合最优解。
- 第一版不支持排队订单使用优惠券。
- 第一版不引入真实第三方支付和退款渠道。
- 第一版不实现评论图片、敏感词自动审核和多级回复。
- 第一版不把点赞数量声称为强一致实时统计；持久化采用 Redis 快速记录 + Kafka 异步回写 + 差异修复。

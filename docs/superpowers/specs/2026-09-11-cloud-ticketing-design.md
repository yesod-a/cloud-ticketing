# Cloud Ticketing 票务交易微服务设计

## 1. 目标与边界

Cloud Ticketing 是一个面向演出、赛事和场馆活动的票务交易平台。第一阶段交付 Gateway、Activity、Inventory、Order 四个服务，完成“查询活动/座位 -> 临时锁定 -> 创建待支付订单 -> 超时释放”的闭环；最终目标再扩展 Payment、Ticket、Reconciliation 三个服务。

项目的核心证明目标不是页面数量，而是以下四个可验证行为：

1. 同一座位在并发请求下最多产生一个有效锁定/订单。
2. 数据库事务提交成功后，业务事件最终可以发布到 Kafka。
3. 重复事件、重复请求和超时任务不会造成重复扣减或非法状态跃迁。
4. Redis、订单和座位状态出现差异时，可以被扫描发现并进入补偿流程。

第一阶段不实现真实支付、真实出票、推荐、优惠券和复杂搜索；支付和出票用清晰的事件契约预留扩展点。

## 2. 用户流程

### 2.1 查询

用户经过 Gateway 查询活动详情和场次座位图。Activity 负责活动、场馆、场次和座位布局的读写；Inventory 负责返回某场次座位的可售、锁定和售出状态。活动详情可以缓存，座位可售状态必须标明查询时间和状态来源。

### 2.2 下单

客户端提交 `idempotencyKey`、场次标识和座位列表。Order 校验用户请求和活动状态后调用 Inventory 的锁定接口。Inventory 使用 Redis Lua 脚本在一个原子操作中检查座位状态、写入订单维度的锁定值和过期时间；锁定成功后 Order 在自己的数据库中创建 `PENDING_PAYMENT` 订单，并在同一事务内写入 Outbox 事件 `OrderCreated`。若订单落库失败，Inventory 通过释放接口撤销锁定；若释放也失败，交由后续对账发现。

### 2.3 超时释放

第一阶段使用 XXL-JOB 每分钟扫描超过支付窗口仍为 `PENDING_PAYMENT` 的订单。Order 通过条件更新把订单改为 `EXPIRED`，只有更新成功的实例发布 `OrderExpired`；Inventory 消费事件后释放对应座位。释放操作必须幂等，已售出或已释放座位不能被误改回可售。

### 2.4 未来支付与出票

Payment 消费 `OrderCreated`，模拟支付后发布 `PaymentSucceeded` 或 `PaymentFailed`。Order 只接受当前状态允许的支付结果；Ticket 只消费成功支付事件并用 `orderId` 唯一约束创建票码。真实支付和出票在第一阶段之外，不能在简历中声称已经接入。

## 3. 服务边界与数据归属

| 服务 | 负责内容 | 自有数据 | 对外接口 |
|---|---|---|---|
| gateway-service | 路由、统一认证、基础限流、请求关联 ID | 无业务表 | `/api/**` 路由 |
| activity-service | 活动、场馆、场次、座位布局配置 | `activity`、`venue`、`session`、`seat` | 活动/场次/座位布局查询与管理 |
| inventory-service | 座位可售事实、Redis 临时锁、锁定/释放 | `inventory_seat`、`inventory_lock` | 锁定、释放、状态查询 |
| order-service | 订单状态机、幂等请求、Outbox | `orders`、`order_items`、`idempotency_record`、`outbox_event` | 创建订单、查询订单、超时关闭 |
| payment-service（最终目标） | 支付意图、支付回调和回调幂等 | `payment`、`payment_callback` | 模拟支付和回调 |
| ticket-service（最终目标） | 出票、票码和出票幂等 | `ticket` | 票据查询 |
| reconciliation-service（最终目标） | 跨服务差异扫描、修复建议和补偿事件 | `reconciliation_diff`、`repair_record` | 对账报表和人工修复 |

每个服务只写自己的表。第一阶段本地可以使用同一个 MySQL 实例，但使用独立 schema 或数据库名模拟数据隔离；服务之间不能直接修改对方表。

## 4. 同步接口与事件契约

同步接口用于需要即时响应的查询、锁定和状态校验；Kafka 用于传播已经在本地事务中完成的业务事实。

### 4.1 REST 命令

- `GET /api/activities/{activityId}`：活动和场次详情。
- `GET /api/sessions/{sessionId}/seats`：座位状态查询。
- `POST /api/orders`：请求体包含 `sessionId`、座位 ID 列表和 `idempotencyKey`，返回订单号、状态和支付截止时间。
- `GET /api/orders/{orderId}`：订单状态查询。
- `POST /internal/inventory/locks`：内部锁定接口，要求请求关联 ID 和订单意图 ID。
- `POST /internal/inventory/locks/{orderId}/release`：内部释放接口，重复调用返回幂等成功。

### 4.2 Kafka 事件

统一事件字段：`eventId`、`eventType`、`aggregateType`、`aggregateId`、`occurredAt`、`schemaVersion`、`traceId`、`payload`。

| Topic | Key | 第一阶段生产者/消费者 | 语义 |
|---|---|---|---|
| `ticket.order-events.v1` | `orderId` | Order / 未来 Payment、Ticket、Reconciliation | 订单创建、过期等事实 |
| `ticket.inventory-events.v1` | `orderId` | Inventory / Order、Reconciliation | 座位锁定、释放、售出结果 |
| `ticket.retry-events.v1` | `eventId` | 各消费者 / 对应消费者 | 有限重试事件 |
| `ticket.dlq.v1` | `eventId` | 各消费者 / 人工或修复任务 | 不可处理事件 |

同一订单使用 `orderId` 作为 key，尽量保持单订单事件在同一分区有序；消费者不能依赖全局顺序。每个消费者以 `(eventId, consumerName)` 建唯一约束，处理成功和记录消费状态在同一事务内完成。事件生产采用 Outbox，发布器使用 Kafka producer 幂等和发送回调记录结果；重复发布由消费者幂等兜底。

## 5. 核心一致性设计

### 5.1 座位锁定

Redis key：`ticket:seat-lock:{sessionId}:{seatId}`，value 为 `orderId:lockToken`，TTL 为支付窗口，例如 15 分钟。Lua 脚本一次完成：

1. 校验请求座位数量和重复座位。
2. 检查每个 key 是否不存在或属于同一订单。
3. 全部满足时写入所有 key，并设置相同过期时间。
4. 任一座位不可用时返回失败，不写入部分结果。

MySQL `inventory_lock` 记录订单、场次、座位、锁 token、过期时间和状态；`(session_id, seat_id, active)` 通过唯一策略防止有效锁重复。Redis 是并发入口，MySQL 是可审计事实，不能反过来把 Redis 当成最终库存。

### 5.2 订单状态机

允许的状态转换：

```text
PENDING_PAYMENT -> PAID
PENDING_PAYMENT -> EXPIRED
PENDING_PAYMENT -> CANCELLED
PAID -> TICKETED（由未来出票流程使用）
PAID -> REFUNDED（由未来退款流程使用）
```

所有转换采用 `UPDATE ... WHERE order_id=? AND status=?`，并检查影响行数。重复支付、重复关单和过期后的支付只返回当前状态，不覆盖已经生效的终态。订单创建使用客户端幂等键，服务端保存请求摘要，重复请求返回原订单，参数不一致则返回冲突。

### 5.3 Outbox 与消费重试

创建订单或状态转换和 Outbox 写入必须在同一数据库事务。发布器按创建时间批量读取未发送事件，发送成功记录时间，失败进入有限重试；消费者失败按可重试异常进入重试 Topic，超过次数进入 DLQ。消息处理不能依赖 Kafka 的 exactly-once 来替代业务幂等。

## 6. 可靠性与故障处理

| 故障 | 处理 | 最终状态 |
|---|---|---|
| Redis 不可用 | 锁定接口快速失败，不能直接写“已锁定”订单 | 用户可重试，无脏订单 |
| 锁定成功、订单落库失败 | 调用释放；释放失败由对账任务发现 | 不产生可支付订单 |
| Kafka 暂时不可用 | Outbox 保留未发布事件 | 恢复后补发 |
| 消费者重复收到事件 | 查消费幂等表或目标终态 | 不重复出票/释放 |
| 支付回调与超时关闭并发 | 条件更新只允许一个状态转换成功 | PAID 或 EXPIRED，不能双生效 |
| Worker/服务中断 | 定时任务扫描处理中和过期数据 | 重试、补偿或 DLQ |
| Redis 与 MySQL 状态不一致 | 对账服务生成差异记录 | 自动修复或人工处理 |

## 7. 可观测性

每个请求和事件携带 `traceId`，日志至少包含服务名、订单号、场次、座位、事件 ID、Kafka topic/partition/offset、重试次数和错误分类。指标包括：订单创建成功/失败、座位锁定冲突、Outbox 积压、Kafka 消费延迟、重试/DLQ 数量、超时订单数和对账差异数。第一阶段提供健康检查和基础 Actuator 指标；Grafana 面板列为后续增强，不在没有实际搭建时写入简历。

## 8. 验收与测试

- 并发测试：同一场次同一座位发起至少 100 个并发锁定请求，断言成功数不超过 1，数据库有效锁不超过 1。
- 幂等测试：同一个 `idempotencyKey` 重复提交，返回同一订单；相同 key 不同请求摘要返回冲突。
- 竞态测试：支付确认和 XXL-JOB 关单并发执行，断言订单只进入一个合法终态，座位状态与终态一致。
- 消息测试：重复投递同一 `eventId`，断言消费记录和业务状态只生效一次；模拟 Kafka 不可用，断言 Outbox 可补发。
- 释放测试：订单过期后座位恢复可售；重复释放、释放已售座位不改变错误状态。
- 对账测试：人为制造 Redis 锁、库存表和订单状态差异，断言能生成差异记录并执行安全修复。
- 契约测试：Gateway 到各服务的 JSON 字段、错误码、traceId 和事件 schema 版本稳定。

## 9. 非目标与简历边界

第一版不宣称真实支付接入、独立集群部署、生产流量、用户规模、吞吐、延迟或“零超卖”。只有完成压测、故障注入并保存报告后，才可以把相应结果写入简历。个人项目应使用“实现/设计”，不要写“服务数千用户”或“线上稳定运行”等未经证据支持的表述。

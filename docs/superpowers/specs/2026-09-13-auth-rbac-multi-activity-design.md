# Cloud Ticketing 认证、权限与多活动平台设计

## 1. 背景与目标

当前系统前端默认展示“星河现场 · 城市之声”，Activity 服务和迁移脚本只提供一条演示活动数据；现有领域模型已经具备活动、场馆、场次和座位布局的扩展方向，但尚未实现登录、管理员权限和数据库驱动的多活动运营。

本设计的目标是：

1. 增加手机号或邮箱加密码登录，并建立可吊销、可轮换的会话机制。
2. 使用独立 `auth-service` 统一管理用户、角色、权限、数据范围、登录和审计。
3. 采用 Gateway JWT 粗粒度认证、领域服务资源级授权的双层模型。
4. 将活动、场馆、场次、座位布局和库存改为多活动、按场次隔离的数据模型。
5. 提供用户端和管理员端完整的页面、API 和权限边界。
6. 保留现有座位锁、订单幂等、Outbox、超时释放等一致性行为，不引入跨服务直接写表。

本设计不实现真实支付、出票和跨服务对账运行时，仅保留事件契约和后续扩展边界。

## 2. 方案与边界

采用独立 `auth-service` + Gateway JWT + RBAC/数据范围方案。

### 2.1 服务职责

| 服务 | 职责 |
|---|---|
| `auth-service` | 注册、登录、退出、刷新、密码管理、用户、角色、权限、数据范围、登录日志和审计日志 |
| `gateway-service` | 路由、Access Token 签名/有效期/吊销校验、匿名白名单、限流和请求关联 ID |
| `activity-service` | 活动、场馆、场次、座位布局及其管理接口；发布状态校验 |
| `inventory-service` | 按场次的座位可售事实、Redis 临时锁、锁定、释放和库存异常处理 |
| `order-service` | 订单状态机、用户订单查询、幂等、Outbox 和超时关闭 |
| `web` | Vue + Vite 用户端和管理端；只负责交互和体验层路由保护，不能替代后端授权 |

### 2.2 请求链路

```text
Vue -> Gateway -> auth-service       登录、注册、刷新、退出
Vue -> Gateway -> 领域服务           业务请求
Gateway -> 校验 JWT                  签名、有效期、jti、scopeVersion
领域服务 -> 最终授权                  角色权限 + 资源数据范围
```

Gateway 不信任客户端提交的角色、用户 ID 或数据范围。领域服务从可信请求上下文中的 JWT `sub`、角色和权限读取身份，并再次验证资源归属。

## 3. 认证与会话

### 3.1 登录方式

- 普通用户和管理员均使用手机号或邮箱 + 密码。
- 手机号、邮箱分别唯一；登录输入可为任一标识。
- 密码使用 BCrypt 或 Argon2 哈希，永不回显或可逆存储。
- 管理员数据模型预留 MFA 配置、挑战和恢复码字段；第一阶段不强制启用 MFA，但管理员策略可配置为必需。

### 3.2 Token 策略

- Access Token 使用 JWT，默认有效期 15 分钟，声明包括 `sub`、`jti`、`roles`、`permissions`、`scopeVersion`、`iat`、`exp`。
- Refresh Token 使用随机高熵字符串，服务端仅保存哈希；默认有效期 7～30 天，按会话族管理。
- 每次刷新轮换 Refresh Token，旧令牌立即失效；检测到旧令牌重放时吊销整个会话族。
- 退出登录吊销当前 Refresh Token，并将当前 `jti` 加入短期黑名单或提升用户 `tokenVersion`。
- Refresh Token 优先使用 HttpOnly、Secure、SameSite Cookie；Access Token 仅保存在前端内存，避免持久化到 localStorage。

### 3.3 登录安全

- 登录按 IP、账号标识分别限流。
- 连续失败使用递增延迟，达到阈值后临时锁定账号。
- 登录错误统一返回“账号或密码错误”，避免用户枚举。
- 密码重置令牌一次性、短时效；短信/邮件发送渠道作为可插拔适配器。
- 所有登录成功、失败、锁定、退出、刷新和重置事件写入登录日志。

### 3.4 匿名白名单

允许匿名访问：登录、注册、刷新、密码重置请求、已发布活动列表、已发布活动详情、公开场次信息。

必须认证：座位状态详情、选座锁定、下单、订单查询、个人中心和所有管理 API。管理 API 还必须通过角色、权限和数据范围授权。

## 4. 角色、权限与数据范围

采用 RBAC + Scope 双层授权。前端权限只用于菜单和体验控制，后端策略是唯一可信判定。

### 4.1 内置角色

| 角色 | 权限摘要 | 数据范围 |
|---|---|---|
| `USER` | 浏览活动、查询座位、创建/查询/取消自己的订单、维护个人资料 | 仅本人 |
| `OPERATOR` | 活动、场馆、场次、区域、座位布局 CRUD；发布/下线活动 | 授权的活动/场馆 |
| `ORDER_ADMIN` | 查询订单、关闭订单、退款预留、导出 | 授权的活动/场次 |
| `INVENTORY_ADMIN` | 查询库存、释放异常锁、调整可售状态 | 授权的场馆/场次/区域 |
| `AUDITOR` | 查看登录、审计、订单和库存对账信息 | 授权范围内只读 |
| `SUPER_ADMIN` | 用户、角色、权限、范围、系统配置及全部业务操作 | 全量 |

权限采用稳定编码，例如：

```text
activity:read       activity:write       activity:publish
venue:read          venue:write          session:write
seat-layout:read    seat-layout:write
inventory:read      inventory:lock-release inventory:adjust
order:read          order:cancel        order:refund order:export
user:read           user:manage         role:manage scope:manage
audit:read          system:config
```

### 4.2 数据范围规则

- 普通用户查询订单强制增加 `order.user_id = currentUserId`，不能通过修改 URL 访问他人订单。
- 管理员请求的活动、场馆、场次和区域必须命中其授权 Scope；服务端将 Scope 转换为 SQL 条件或领域策略。
- `OPERATOR` 发布活动同时需要 `activity:publish` 和该活动的管理范围。
- `INVENTORY_ADMIN` 手工释放必须填写原因，记录释放前后状态和操作者。
- `AUDITOR` 只有读权限，所有写接口返回 403。
- `SUPER_ADMIN` 可以授予角色和范围，但不能删除系统内置角色；高风险操作需要二次确认并写审计日志。
- 角色或 Scope 变更递增 `scopeVersion`，Gateway 拒绝旧版本 Token，确保权限快速生效。

## 5. 多活动、场馆、场次与座位模型

```text
Activity
  ├─ Venue
  │    └─ SeatLayout / Area / Row / Seat
  └─ Session
       └─ InventorySeat
```

### 5.1 领域实体

- `activity`：名称、描述、海报、主办方、状态（`DRAFT`、`PUBLISHED`、`SOLD_OUT`、`ENDED`、`OFFLINE`）。
- `venue`：名称、地址、容量和布局版本。
- `session`：活动、场馆、开始/结束时间、售票窗口和状态。
- `seat_layout`：区域、排、座位坐标、票档和价格；发布后布局版本不可原地修改。
- `inventory_seat`：按 `session_id + seat_id` 唯一，状态为 `AVAILABLE`、`LOCKED`、`SOLD` 或 `DISABLED`。

活动、场次、库存和订单均使用 ID 隔离。发布时校验时间、价格和布局完整性，生成不可变布局版本，并初始化该场次的库存行。下线只影响公开查询，不删除历史订单和库存事实。

### 5.2 管理后台

- `/admin/activities`：草稿、编辑、发布、下线、复制。
- `/admin/venues`：场馆和座位布局版本。
- `/admin/sessions`：场次、售票窗口和票价。
- `/admin/orders`：按活动、场次和用户筛选，关闭订单，退款接口预留。
- `/admin/inventory`：座位状态、锁定详情和异常锁释放。
- `/admin/users`、`/admin/roles`：用户、管理员、角色和 Scope 授权。
- `/admin/audit-logs`：登录、权限变更、活动发布和库存修复审计。

## 6. API 契约

### 6.1 认证 API

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/password/forgot
POST /api/auth/password/reset
GET  /api/auth/me
```

### 6.2 用户和公开活动 API

```text
GET  /api/activities?page=&size=&keyword=
GET  /api/activities/{activityId}
GET  /api/sessions/{sessionId}/seats
GET  /api/orders/{orderId}
POST /api/orders
POST /api/orders/{orderId}/cancel
GET  /api/me/orders
```

`GET /api/orders/{orderId}` 和取消接口均在服务端校验当前用户或管理员 Scope；越权资源统一返回 404，避免泄露资源存在性。

### 6.3 管理 API

```text
/api/admin/activities
/api/admin/venues
/api/admin/sessions
/api/admin/inventory/**
/api/admin/orders/**
/api/admin/users
/api/admin/roles
/api/admin/scopes
/api/admin/audit-logs
```

统一响应 `{code, message, traceId, data}`。认证失败为 401，权限不足为 403，越权资源为 404。写接口按资源类型使用 `Idempotency-Key` 或条件更新保证幂等。

## 7. 数据库设计

`auth-service` 使用独立 schema，建议表：

- `auth_user`：手机号、邮箱、密码哈希、昵称、状态、失败次数、锁定时间。
- `auth_role`、`auth_permission`、`auth_user_role`、`auth_role_permission`：RBAC 关系。
- `auth_scope`、`auth_user_scope`：活动、场馆、场次或区域授权。
- `auth_refresh_token`：Token 哈希、会话族、过期和吊销状态。
- `auth_login_log`：成功/失败、IP、设备和原因。
- `auth_audit_log`：操作者、动作、资源、前后值摘要、traceId。

角色、权限和审计关联使用禁用或软删除，不能物理删除导致历史日志失真。密码、Token 原文和敏感恢复码不得出现在接口响应或普通日志中。

Activity 服务继续拥有 `activity`、`venue`、`session`、`seat_layout` 和座位布局相关表；Inventory 继续拥有 `inventory_seat`、`inventory_lock`；Order 继续拥有订单、订单项、幂等和 Outbox 表。服务之间禁止直接修改对方表。

## 8. 前端页面与状态管理

### 8.1 用户端

```text
/login
/register
/forgot-password
/activities
/activities/:id
/sessions/:id/seats
/account/profile
/account/orders
/account/orders/:id
```

首页通过 `GET /api/activities` 获取活动列表，不再固定写入单一演示活动。活动详情选择场次后，座位图通过场次 ID 加载。

### 8.2 管理端

```text
/admin/login
/admin/activities
/admin/venues
/admin/sessions
/admin/orders
/admin/inventory
/admin/users
/admin/roles
/admin/audit-logs
```

Vue Router 守卫只负责体验层拦截；API 客户端收到 401 时最多自动刷新一次 Token，刷新失败清空会话并回到登录页；403 显示无权限页。动态菜单依据权限生成，但所有管理接口仍由后端重新鉴权。

## 9. 测试与验收

### 9.1 测试层次

1. Auth：注册唯一性、密码强度、登录失败锁定、刷新轮换、退出吊销、401/403。
2. 授权矩阵：角色对权限的允许/拒绝，以及跨活动、跨场馆、跨用户访问拒绝。
3. 活动管理：草稿发布校验、布局版本不可变、下线不影响历史订单、多活动列表隔离。
4. 端到端：注册/登录 -> 活动列表 -> 选座 -> 下单 -> 查询订单；管理员发布活动后用户可见。
5. 安全回归：伪造角色、过期 Token、Refresh 重放、越权 URL、绕过 Gateway 访问内部接口。
6. 保留现有并发锁、订单幂等、Outbox、超时释放和 Kafka 恢复测试。

### 9.2 验收标准

- 新用户能使用手机号或邮箱注册、登录、刷新和退出。
- 未登录用户不能锁座、下单或访问管理端。
- 普通用户不能读取他人订单，管理员不能操作未授权资源。
- 至少存在两条活动数据，列表、详情、场次、座位和订单按 ID 正确隔离。
- 每个管理员写操作都能追溯到操作者、资源、时间和 traceId。
- 现有订单、库存和消息一致性测试无回归。

## 10. 分阶段交付

1. **Phase 1 身份基础**：`auth-service`、Gateway JWT、注册/登录/退出、`USER` 角色。
2. **Phase 2 多活动运营**：活动、场馆、场次、布局 CRUD，活动列表/详情和发布流程。
3. **Phase 3 管理员 RBAC**：角色、权限、Scope、管理后台和审计日志。
4. **Phase 4 安全与验收**：Refresh 轮换、锁定/限流、授权矩阵、端到端测试和 Docker Compose 更新。

Payment、Ticket、Reconciliation 在本设计中仅保留事件契约，不宣称已实现真实运行时能力。

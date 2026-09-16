# 管理端业务闭环设计

## 目标

在现有认证、活动、座位和订单基础上，补齐管理员可执行、可追踪、可持久化的运营闭环，并为列表提供分页、条件筛选和排序。

## 业务闭环

管理员创建活动与场次，配置座位并发布活动；用户从公开活动进入真实场次和座位完成下单；管理员查看订单、取消订单或调整库存；活动、座位、订单和权限变更全部写入数据库并可查询审计记录。

## 权限

- `activity:write`：创建和编辑活动、场次、场馆。
- `activity:publish`：发布、下线活动。
- `seat-layout:write`：创建和调整座位布局。
- `inventory:read` / `inventory:adjust` / `inventory:lock-release`：查看和操作库存。
- `order:read` / `order:cancel` / `order:refund` / `order:export`：订单查询和处理。
- `user:read` / `user:manage`：用户查询和状态管理。
- `audit:read`：查看审计记录。
- `SUPER_ADMIN`：拥有全部权限。

## 列表体验

管理列表统一支持 `page`（从 0 开始）、`size`（1-100）、关键字、状态和排序参数。接口返回 `items`、`page`、`size`、`total`、`totalPages`，前端提供筛选表单、分页按钮、空状态和错误提示。

## 数据约束

所有业务列表和详情必须来自 MySQL。前端不得生成活动、场次、座位、订单或用户模拟数据。订单和库存的关键状态更新必须在服务端完成权限校验并记录审计事件。


# Redis 座位状态三阶段迁移设计

## 目标

在不降低库存正确性的前提下，把座位查询和高并发锁座逐步迁移到 Redis：Bitmap 保存场次级二进制状态，Lua 保证多座位锁定的原子性，MySQL 继续保存订单、有效锁和审计事实。每一阶段都可通过配置关闭，并保存可复现的性能与一致性证据。

## 现状与边界

- 当前库存最终状态由 `inventory_seat` 的条件更新和 `inventory_lock` 持久化记录保证。
- Redis/Caffeine 已用于活动公开读缓存和 Gateway 令牌状态缓存；当前没有 Redis 座位锁。
- 座位是场次级快照，必须为每个场次建立稳定的 `seatIndex`，不能直接把 UUID 当 Bitmap 下标。
- Redis 与 MySQL 不共享事务，Redis 成功后 MySQL 失败必须通过释放和对账收敛。
- 阶段一不改变锁座和支付业务状态，只把公共座位读取改为可回源的 Bitmap 投影。

## 三阶段方案

### 阶段一：Bitmap 只读投影

为每个场次建立稳定座位索引，并维护 `sold`、`locked`、`disabled` 三个 Bitmap。公共座位查询优先读取 Redis，缓存未命中或 Redis 异常时从 MySQL 回源并重建。MySQL 写操作成功后使对应场次投影失效或重建，避免把 Redis 当成最终事实。

验收：座位状态与 MySQL 抽样一致；Redis 故障时读路径可回源；记录 DB 查询数、Redis 命中率、p50/p95/p99 和内存。

### 阶段二：Lua 原子锁座

增加 `cloudticket.inventory.redis-lock.enabled` 开关，默认关闭。开启后，同一场次的锁座请求使用 Redis Lua：校验 sold/disabled Bitmap 和 hold Key，全部可用后以 `SET NX PX` 写入临时锁；任一座位冲突则整单失败。hold value 携带 `orderId:lockToken`，释放必须校验 token。Redis 锁成功后同步写入 MySQL `inventory_lock`；数据库失败则执行补偿释放并记录异常。

Redis Cluster 下同一场次的 key 使用相同 hash tag。任何 Redis 不可用、脚本超时或状态不确定都拒绝新锁，不与数据库锁座路径混用。

验收：同一座位并发成功数不超过 1；多座位锁定全成功或全失败；重复锁座、释放、过期和 Redis 故障均可恢复；与 MySQL 基线比较锁座延迟和数据库写入量。

### 阶段三：事件同步与对账

订单、支付、取消、过期和确认状态通过 Outbox/Kafka 驱动 Redis 投影更新。增加对账扫描，比较 MySQL 有效锁、订单终态、sold Bitmap 和 hold Key，区分可自动修复与需人工处理的冲突。Outbox 发布增加 claim/lease、退避和最大重试边界，避免多实例重复发布。

验收：Redis 清空后可从 MySQL 重建；Kafka 暂停后恢复可补齐投影；支付/过期并发只产生一个合法终态；故障注入结果和指标报告可复现。

## Key 约定

```text
cloudticket:inventory:{sessionId}:sold
cloudticket:inventory:{sessionId}:locked
cloudticket:inventory:{sessionId}:disabled
cloudticket:inventory:{sessionId}:version
cloudticket:inventory:hold:{sessionId}:{seatIndex}
```

`{sessionId}` 是 Redis Cluster hash tag。Bitmap 只保存二进制状态，座位元数据、订单号、锁 token 和过期时间不放入 Bitmap。

## 失败策略

| 故障 | 处理 |
|---|---|
| Redis 读失败 | 阶段一回源 MySQL；阶段二不接受新锁座 |
| Redis 锁成功、MySQL 失败 | 使用 token 释放 Redis，记录补偿任务 |
| MySQL 有效锁、Redis 缺少 hold | 对账恢复 Redis hold，不能直接创建订单 |
| Redis 存在孤儿 hold | 只有确认无有效订单/锁后才删除 |
| Redis Bitmap 与 MySQL 不一致 | MySQL 事实优先重建；支付/终态冲突转人工 |
| 多实例同时发布 Outbox | 使用 claim/lease，发布超时可重新领取 |

## 性能指标

每轮压测固定 CPU、内存、线程数、场次和座位数据，保存：

- 座位读 QPS、p50/p95/p99、错误率、Redis 命中率。
- 锁座成功/冲突数、p50/p95/p99、MySQL UPDATE/INSERT 数量。
- Redis 内存、命令耗时、连接数；MySQL CPU、锁等待和连接数。
- 阶段一、阶段二分别与同一环境的 MySQL 基线对比，不把本地数据外推为生产容量。

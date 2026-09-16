# Task 1 报告：数据库座位模板与服务层

## 实现内容

- 新增 `V6__venue_seat_template.sql`：为 `venue` 增加 `capacity`，新增 `venue_seat` 模板表、唯一约束 `(venue_id,row_label,seat_number)` 和场馆外键；同时保留 `position` 字段并提供 `position_x`/`position_y` 坐标列。
- `ActivityCatalog` 增加 `VenueSeat` 记录及模板查询、创建、更新、删除方法；四参数 `createVenueSeat` 默认状态为 `AVAILABLE`，兼容现有五参数调用。
- `createSession` 校验 ISO-8601 开始/结束时间顺序和 venue/activity 归属，并执行 `INSERT ... SELECT` 将模板座位复制到新场次。
- 保留三参数 `createVenue` 与四参数容量版本，并为 `Venue` record 提供无容量兼容构造器，避免破坏既有控制器测试。

## TDD 证据

先新增 `VenueSeatTemplateTest`，覆盖模板字段持久化、时间倒置拒绝、模板复制 SQL。

RED：

```text
mvn --% -q -pl services/activity-service -Dtest=VenueSeatTemplateTest test
# FAIL：无法解析 reactor 依赖 common-web（未使用 -am）

mvn --% -q -pl services/activity-service -am -Dtest=VenueSeatTemplateTest -Dsurefire.failIfNoSpecifiedTests=false test
# FAIL：四参数 createVenueSeat 不存在；既有 Venue 构造器兼容性错误；随后修正 Mockito SQL stub
```

GREEN：

```text
mvn --% -q -pl services/activity-service -am -Dtest=VenueSeatTemplateTest -Dsurefire.failIfNoSpecifiedTests=false test
# PASS：3 tests, 0 failures, 0 errors

mvn --% -q -pl services/activity-service -am test
# PASS：13 tests, 0 failures, 0 errors
```

## 备注

- 工作区保持在 `master`，未重置、删除或覆盖其他未提交改动。
- 代码已提交：`6ccce2d feat: add venue seat templates`。任务报告文件保留在 SDD 目录，供父任务汇总。

# 场馆库存独立化与座位布局管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将场馆从“活动附属”升级为独立库存资源，管理端提供“场馆与库存”一级页面，支持多种座位布局生成方式；活动管理仅保留业务信息与场次管理；用户端以可视化座位图展示真实座位。

**Architecture:** 场馆不再强制绑定活动，座位模板按区域组织并携带排、座号、显示名与坐标。创建场次时把场馆座位模板复制为场次库存快照；管理端通过真实 API 管理场馆和座位，用户端读取带布局信息的座位并分区渲染。

**Tech Stack:** Spring Boot 3、JdbcTemplate、Flyway、MySQL、Vue 3、Vite、TypeScript、Vitest。

**Spec:** `docs/superpowers/specs/2026-09-15-admin-business-loop-design.md` 及本轮确认的场馆/座位设计。

## Global Constraints

- 所有列表、下拉项和详情必须来自 MySQL API，禁止前端模拟数据。
- 写操作校验现有权限/Scope 并写审计日志。
- 开始时间必须早于结束时间。
- 不切换分支；改动只落在当前 `master`。

### Task 1: 场馆独立化与座位布局数据模型

**Files:**
- Create: `services/activity-service/src/main/resources/db/migration/V7__standalone_venue_layout.sql`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityCatalog.java`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/VenueLayoutTest.java`

**Interfaces:**
- `venue.activity_id` 可为空；`venue_seat` 增加 `area_label`、`display_name`、`seat_type`、`enabled`；`seat` 增加 `area_label`、`display_name`、`seat_type`、`position_x`、`position_y`。
- `ActivityCatalog.generateVenueLayout(String venueId, String mode, Map<String,Object> rules)` 返回 `List<VenueSeat>`；先删除该场馆旧模板再生成。
- `createSession` 不再校验 venue 归属活动，复制模板时同步复制区域、显示名、类型和坐标。

- [ ] **Step 1: Write failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Implement migration and catalog methods**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

### Task 2: 场馆库存管理 API

**Files:**
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/VenueAdminController.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityAdminController.java`

**Interfaces:**
- `GET /api/admin/venues?keyword=&page=&size=` 返回分页场馆。
- `POST /api/admin/venues` 创建场馆 `{name,address}`。
- `PUT /api/admin/venues/{id}` 更新 `{name,address}`。
- `POST /api/admin/venues/{id}/layout` 批量生成座位 `{mode,areaLabel,rowCount,seatsPerRow,rowLabelType,startSeatNumber,rows}`。
- `GET /api/admin/venues/{id}/seats` 返回座位模板。
- 活动场次新增 `PUT /{activityId}/sessions/{sessionId}` 与 `POST /{activityId}/sessions/{sessionId}/publish`、`/offline`。

- [ ] **Step 1: Write failing controller tests**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Implement controllers**
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

### Task 3: 前端 API 封装与失败测试

**Files:**
- Modify: `web/src/adminApi.ts`
- Create: `web/src/__tests__/venue-layout.test.ts`

**Interfaces:**
- 新增 `createAdminVenueStandalone`、`updateAdminVenue`、`adminVenuePage`、`generateAdminVenueLayout`、`updateAdminSession`、`publishAdminSession`、`offlineAdminSession`。
- 新增 `toLocalIso`、`assertTimeOrder` 纯函数供 UI 与测试使用。

- [ ] **Step 1: Write failing Vitest cases**
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement API functions**
- [ ] **Step 4: Run tests to verify pass**
- [ ] **Step 5: Commit**

### Task 4: 管理端“活动管理”与“场馆与库存”页面重构

**Files:**
- Modify: `web/src/views/admin/AdminLayout.vue`
- Create: `web/src/views/admin/VenueInventoryView.vue`
- Modify: `web/src/styles.css`

**Interfaces:**
- 一级导航：活动管理、场馆与库存、订单管理、退款审核、用户与角色、资源 Scope、审计日志。
- 活动管理：创建/编辑活动、创建/编辑/发布/下线场次；创建场次从全局场馆列表选择。
- 场馆与库存：场馆分页列表、创建/编辑场馆、座位布局编辑器（统一行列 / 每行独立列数 / 单座增删改）、预览图。

- [ ] **Step 1: Write failing UI test**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement VenueInventoryView and refactor AdminLayout**
- [ ] **Step 4: Run Vitest and Vite build**
- [ ] **Step 5: Commit**

### Task 5: 用户端座位图美化

**Files:**
- Modify: `web/src/views/BookingView.vue`
- Modify: `web/src/types.ts`
- Modify: `web/src/api.ts`
- Modify: `web/src/styles.css`

**Interfaces:**
- `Seat` 增加 `areaLabel`、`displayName`、`x`、`y`、`type`。
- 座位按区域分组，依据坐标渲染成舞台朝向的可视化座位图；已售/锁定/可选状态有明确视觉区分。

- [ ] **Step 1: Write failing UI test**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement grouped seat map**
- [ ] **Step 4: Run Vitest and Vite build**
- [ ] **Step 5: Commit**

### Task 6: 集成验证与证据记录

- [ ] **Step 1:** `mvn -q test`
- [ ] **Step 2:** `npm run test -- --run` 与 `npm run build`
- [ ] **Step 3:** `docker compose up -d --build web activity-service gateway`
- [ ] **Step 4:** 实测登录→场馆 CRUD→生成座位→创建场次→查询座位。
- [ ] **Step 5:** 更新 `docs/evidence/auth-rbac-multi-activity-verification.md`。

# 场馆与座位配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 master 分支为管理员提供可持久化的场馆、座位模板、活动弹窗和场次配置流程，确保前端只能使用数据库真实数据。

**Architecture:** 新增 `venue_seat` 场馆座位模板表；场馆属于活动，创建场次时从模板复制座位到该场次，避免不同场次共享库存。管理端通过真实 API 读取和写入活动、场馆、座位模板及场次，活动创建成功后打开配置弹窗。后端所有写操作校验现有权限/Scope 并写入审计日志。

**Tech Stack:** Spring Boot 3、JdbcTemplate、Flyway、MySQL、Vue 3、Vite、TypeScript、Vitest。

**Spec:** `docs/superpowers/specs/2026-09-15-admin-business-loop-design.md` 及本轮确认的场馆/座位配置设计。

## Global Constraints

- 所有业务列表、下拉项和详情必须来自 MySQL API，禁止前端模拟活动、场馆、场次或座位数据。
- `activity:write`、`venue:write`、`seat-layout:write` 或 `system:config` 才能执行对应写操作；Scope 必须限制到目标活动。
- 开始时间必须早于结束时间；API 接收 ISO-8601 时间戳并在服务端再次校验。
- 不切换分支，不重置或覆盖用户已有未提交修改。

### Task 1: 数据库座位模板与服务层

**Files:**
- Create: `services/activity-service/src/main/resources/db/migration/V6__venue_seat_template.sql`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityCatalog.java`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/VenueSeatTemplateTest.java`

**Interfaces:**
- `ActivityCatalog.createVenue(String activityId,String name,String address)` 保持兼容并初始化空模板。
- 新增 `List<VenueSeat> venueSeats(String venueId)`、`VenueSeat createVenueSeat(String venueId,String rowLabel,int seatNumber,String position)`、`VenueSeat updateVenueSeat(String id,...)`、`void deleteVenueSeat(String id)`。
- `createSession(...)` 校验 venue 属于 activity、时间顺序正确，并把该 venue 的模板座位复制到新 session。

- [ ] **Step 1: Write the failing test**
  - 使用 Mockito/JdbcTemplate mock 验证座位模板新增参数被持久化；验证开始时间不早于结束时间抛出 `IllegalArgumentException`；验证创建场次会执行模板复制 SQL。
- [ ] **Step 2: Run test to verify it fails**
  - Run `mvn -pl services/activity-service -Dtest=VenueSeatTemplateTest test`; Expected: FAIL because methods/table do not exist。
- [ ] **Step 3: Write minimal implementation**
  - 建立 `venue_seat` 表（`id`、`venue_id`、`row_label`、`seat_number`、`position_x`、`position_y`、`status`，唯一键 `venue_id,row_label,seat_number`，外键到 `venue`）。
  - 在 `ActivityCatalog` 增加 record `VenueSeat` 与 CRUD 方法；创建场次时执行 `INSERT ... SELECT` 复制模板，模板为空时允许场次创建但返回空座位。
- [ ] **Step 4: Run test to verify it passes**
  - 重跑同一 Maven 测试，Expected: PASS。
- [ ] **Step 5: Commit**
  - `git add services/activity-service/src/main/resources/db/migration/V6__venue_seat_template.sql services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityCatalog.java services/activity-service/src/test/java/com/cloudticket/activity/VenueSeatTemplateTest.java && git commit -m "feat: add venue seat templates"`

### Task 2: 管理员场馆/座位/场次 API 与权限审计

**Files:**
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/VenueAdminController.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityAdminController.java`
- Create/Modify: `services/activity-service/src/test/java/com/cloudticket/activity/VenueAdminControllerTest.java`

**Interfaces:**
- `GET /api/admin/venues?activityId=` 返回场馆列表。
- `POST /api/admin/venues` 创建场馆。
- `GET /api/admin/venues/{venueId}/seats` 返回座位模板。
- `POST /api/admin/venues/{venueId}/seats`、`PUT .../{seatId}`、`DELETE .../{seatId}` 管理模板座位。
- `POST /api/admin/activities/{activityId}/sessions` 创建场次；补充 `PUT` 更新和 `POST /{sessionId}/offline` 下线。

- [ ] **Step 1: Write the failing tests**
  - 覆盖无权限返回 forbidden；跨活动 venueId 被拒绝；座位新增/更新/删除成功写审计；时间倒置返回 400。
- [ ] **Step 2: Run tests to verify they fail**
  - Run `mvn -pl services/activity-service -Dtest=VenueAdminControllerTest test`; Expected: FAIL on missing mappings。
- [ ] **Step 3: Implement controllers**
  - 复用现有 `X-User-Permissions`、`X-User-Scopes`、`X-User-Id`、`X-Trace-Id` 头；每次写操作调用 `ScopeAccess` 并记录 before/after。
- [ ] **Step 4: Run tests to verify they pass**
  - 重跑控制器测试并执行 `mvn -pl services/activity-service test`。
- [ ] **Step 5: Commit**
  - `git add services/activity-service/src/main/java services/activity-service/src/test/java && git commit -m "feat: expose venue seat management APIs"`

### Task 3: 前端 API 封装与失败测试

**Files:**
- Modify: `web/src/adminApi.ts`
- Modify: `web/src/__tests__/admin-console.test.ts`
- Create: `web/src/__tests__/venue-seat-management.test.ts`

**Interfaces:**
- 新增 `createAdminVenue`、`adminVenueSeats`、`createAdminVenueSeat`、`updateAdminVenueSeat`、`deleteAdminVenueSeat`、`updateAdminSession`、`offlineAdminSession`。
- API 返回统一解包 `items/data`，错误保留服务端消息。

- [ ] **Step 1: Write failing Vitest cases**
  - 点击创建活动显示弹窗；提交后调用 `/api/admin/activities`；创建场馆后刷新并可选；座位模板 CRUD 使用真实 id；倒置时间阻止请求；`endsAt.min` 跟随 `startsAt`。
- [ ] **Step 2: Run tests to verify failure**
  - Run `npm run test -- --run web/src/__tests__/venue-seat-management.test.ts`; Expected: FAIL。
- [ ] **Step 3: Implement API functions**
  - 在 `adminApi.ts` 按现有 `api<T>` 模式添加函数和类型。
- [ ] **Step 4: Run tests to verify pass**
  - 重跑针对性测试，Expected: API 断言通过，UI 断言仍可能等待 Task 4。

### Task 4: Vue 管理端弹窗、场馆和场次界面

**Files:**
- Modify: `web/src/views/admin/AdminLayout.vue`
- Modify: `web/src/styles.css`

**Interfaces:**
- 活动列表保留分页/筛选；“创建活动”打开模态框。
- 创建成功后打开活动配置模态框，包含“场馆与座位模板”“场次”两个区域。
- 所有下拉选项由 `adminVenues(activityId)` 加载；支持新增场馆、增删改座位模板；支持场次筛选、分页、更新和下线。

- [ ] **Step 1: Implement state and modal events**
  - 增加 `showActivityModal`、`showConfigModal`、`showVenueModal`、`showSeatModal`；关闭时清理错误和表单。
- [ ] **Step 2: Implement date helper and validation**
  - `toIso(local)` 将 `YYYY-MM-DDTHH:mm` 转为 ISO；提交前比较 `Date.parse`，结束时间 `<` 开始时间时显示错误并不调用 API；结束控件绑定 `:min="startsAt"`。
- [ ] **Step 3: Implement venue/seat template UI**
  - 新增场馆后刷新真实列表并自动选中；座位模板表格展示排、号、位置、状态和操作。
- [ ] **Step 4: Implement session UI**
  - 选择场馆后创建场次；场次列表展示场馆、开始/结束时间、状态和操作；成功后刷新当前页。
- [ ] **Step 5: Run Vitest and Vite build**
  - `npm run test -- --run` 与 `npm run build`，Expected: PASS。

### Task 5: 集成验证与证据记录

**Files:**
- Modify: `docs/evidence/auth-rbac-multi-activity-verification.md`
- Modify: `docs/evidence/phase-1-verification.md`

- [ ] **Step 1: Run backend tests**
  - `mvn test`，记录总测试数和失败数。
- [ ] **Step 2: Run frontend tests/build**
  - `npm run test -- --run`、`npm run build`。
- [ ] **Step 3: Recreate Docker services**
  - `docker compose up -d --build activity-service gateway-service web`；确认 `http://127.0.0.1:5173/` 和 `http://127.0.0.1:8080/actuator/health` 可访问。
- [ ] **Step 4: Exercise real API flow**
  - 使用管理员登录获取 token，创建活动→创建场馆→创建座位模板→创建场次→查询场次和座位，记录响应 traceId 和数据库结果。
- [ ] **Step 5: Update evidence docs**
  - 写明命令、时间、结果、已知限制（例如旧场馆无模板时场次座位为空）。

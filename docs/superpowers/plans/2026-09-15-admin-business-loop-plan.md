# Admin Business Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver a database-backed admin console with paginated/filterable lists and an end-to-end activity, inventory, order, user, and audit workflow.

**Architecture:** Activity-service owns activities, venues, sessions, seats, and audit records. Order-service owns persistent orders and exposes user/admin list and state transitions. Auth-service remains the source of users, roles, and permissions; gateway forwards trusted JWT identity headers to downstream services. Vue consumes only gateway APIs and renders admin modules according to permissions.

**Tech Stack:** Spring Boot 3.5, JdbcTemplate, Flyway, MySQL 8.4, Spring Cloud Gateway, Vue 3, Vite, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-15-admin-business-loop-design.md`

## Global Constraints

- Work only on `master` and preserve existing uncommitted changes.
- No frontend business mock data.
- All list endpoints use bounded pagination (`size` 1-100) and return `items/page/size/total/totalPages`.
- Mutating admin endpoints require the relevant trusted JWT permission header.

### Task 1: Persisted activity, session, seat, and audit operations

**Files:** activity-service pom/config/migrations/catalog/controllers/tests.

- Add admin list/create/update/publish/offline/session/seat endpoints backed by MySQL.
- Add keyword/status filters, sorting, and pagination to activity and audit list queries.
- Record an audit row for each admin mutation.

### Task 2: Persistent orders and inventory-facing admin APIs

**Files:** order-service pom/config/migrations/controller/tests; inventory-service persistence/config/controller/tests.

- Store orders and order seats in `order_db`.
- Add user/admin paginated order lists and cancel/refund transitions.
- Add inventory list and seat status updates backed by the activity seat source or synchronized inventory table.

### Task 3: Auth user administration APIs

**Files:** auth-service repository/service/controller/tests.

- Add paginated user search with status/keyword filters.
- Add administrator enable/disable and role assignment endpoints with permission checks and audit event publication.

### Task 4: Vue admin console

**Files:** `web/src/views/admin/*`, `web/src/adminApi.ts`, `web/src/App.vue`, styles and tests.

- Replace static admin navigation with module tabs and real data tables.
- Implement activity, order, inventory, user, and audit screens with filter forms and pagination.
- Wire mutations to authenticated APIs and refresh list state after successful changes.

### Task 5: Verification and evidence

- Run focused and full Maven tests, Vitest, Vite build, Docker rebuild, live API checks for admin and user flows, `git diff --check`, and update evidence documentation.

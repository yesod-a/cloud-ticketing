# Authentication, RBAC, and Multi-Activity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add password authentication, backend-enforced RBAC and data scopes, database-driven multi-activity management, and matching Vue/Vite user and administrator flows while preserving the existing seat-locking and order consistency behavior.

**Architecture:** Add an independent `auth-service` with its own schema for users, sessions, RBAC, scopes, login records, and audit records. Gateway validates short-lived JWT access tokens and refresh-session state; activity, inventory, and order services perform resource-level authorization using trusted identity claims and scope checks. The frontend becomes a database-driven Vue/Vite application with public activity pages, authenticated account pages, and permission-aware administrator routes.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Cloud 2025.0.0, Spring Cloud Alibaba 2025.0.0.0, MySQL 8, Redis, Kafka, Nacos, Maven, Vue 3, Vite, TypeScript, Vitest, Vue Test Utils, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-13-auth-rbac-multi-activity-design.md`

## Global Constraints

- Access Tokens are JWTs with a default 15-minute lifetime; Refresh Tokens are hashed server-side and rotated on every refresh.
- Passwords use BCrypt or Argon2 and are never returned or logged.
- Gateway authentication is not a substitute for resource authorization in domain services.
- The backend trusts the JWT subject, never a client-supplied `userId`, role, or scope.
- Ordinary users can access only their own profile and orders.
- Administrator writes require both a permission and a matching activity, venue, session, or area scope.
- Every administrator write records actor, resource, action, time, trace ID, and a before/after summary in an audit log.
- Services write only their own database tables; cross-service state changes use REST contracts or Kafka events.
- Published activity layout versions are immutable; a changed layout creates a new version.
- Existing Redis/Lua seat locking, order idempotency, Outbox, timeout release, and Kafka recovery behavior must remain green.
- Do not claim real payment, ticket issuance, production traffic, zero oversell, throughput, or latency without saved test evidence.
- The current frontend exists only in `.worktrees/cloud-ticketing-phase1/web`; preserve its uncommitted changes while integrating it into the main project.

---

### Task 1: Add the auth-service module and authentication schema

**Files:**
- Create: `services/auth-service/pom.xml`
- Modify: `pom.xml`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/AuthApplication.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/domain/UserEntity.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/domain/RefreshTokenEntity.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/domain/RoleEntity.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/domain/PermissionEntity.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/domain/ScopeEntity.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/repository/*.java`
- Create: `services/auth-service/src/main/resources/application.yml`
- Create: `services/auth-service/src/main/resources/db/migration/V1__auth_schema.sql`
- Create: `services/auth-service/src/test/java/com/cloudticket/auth/AuthSchemaTest.java`

**Interfaces:**
- Consumes: MySQL connection settings and the existing `common-web` trace/error contract.
- Produces: `auth_user`, `auth_role`, `auth_permission`, `auth_user_role`, `auth_role_permission`, `auth_scope`, `auth_user_scope`, `auth_refresh_token`, `auth_login_log`, and `auth_audit_log` tables.

- [ ] **Step 1: Add the module and a schema smoke test**

Add `services/auth-service` to the root `<modules>` list and add Spring Boot Web, Validation, Security, JDBC or JPA, Flyway, MySQL, and `common-web` dependencies. Write a test that asserts the migration resource exists and includes unique constraints for `email` and `phone`.

- [ ] **Step 2: Run the new test and verify the expected failure**

Run `mvn -pl services/auth-service -DskipTests=false test`. Expected: FAIL because the application and migration have not been created.

- [ ] **Step 3: Create the auth schema**

Create `V1__auth_schema.sql` with UUID or binary-ID primary keys, timestamps, status columns, unique nullable `phone` and `email`, password hash, failed-login counters, lock expiry, token-family IDs, scope resource type/ID, and immutable audit rows. Seed built-in roles `USER`, `OPERATOR`, `ORDER_ADMIN`, `INVENTORY_ADMIN`, `AUDITOR`, and `SUPER_ADMIN` plus the permission codes from the spec.

- [ ] **Step 4: Add domain records and repositories**

Expose repository methods with explicit ownership semantics:

```java
Optional<UserEntity> findByPhoneOrEmail(String identifier);
boolean existsByPhone(String phone);
boolean existsByEmail(String email);
Optional<RefreshTokenEntity> findActiveByHash(String tokenHash);
List<ScopeEntity> findScopesForUser(UUID userId);
```

- [ ] **Step 5: Run the schema tests**

Run `mvn -pl services/auth-service test`. Expected: PASS, with the migration parsed and the module compiling on Java 21.

- [ ] **Step 6: Commit the authentication module foundation**

```bash
git add pom.xml services/auth-service
git commit -m "feat: add auth service schema and domain foundation"
```

### Task 2: Implement registration, login, refresh, logout, and password reset

**Files:**
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/security/PasswordPolicy.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/security/TokenService.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/service/AuthService.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/api/AuthController.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/api/AuthDtos.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/api/AuthExceptionHandler.java`
- Create: `services/auth-service/src/test/java/com/cloudticket/auth/AuthServiceTest.java`
- Create: `services/auth-service/src/test/java/com/cloudticket/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UserEntity`, refresh-token repository, BCrypt/Argon2 encoder, JWT signing key configuration.
- Produces: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `POST /api/auth/password/forgot`, `POST /api/auth/password/reset`, and `GET /api/auth/me`.

- [ ] **Step 1: Write failing service tests**

Cover duplicate phone/email, password policy rejection, successful password verification, failed-login counter increment, temporary lock after the configured threshold, Refresh Token rotation, and replay revocation of the whole token family.

- [ ] **Step 2: Run the focused tests to verify failure**

Run `mvn -pl services/auth-service -Dtest=AuthServiceTest test`. Expected: FAIL because service methods and token storage do not exist.

- [ ] **Step 3: Implement password and session services**

Use a password encoder; normalize email to lowercase and phone to a canonical format. Store only a SHA-256 or HMAC digest of Refresh Tokens. Issue JWT claims with `sub`, `jti`, `roles`, `permissions`, and `scopeVersion`; never include password, phone verification secrets, or raw Refresh Tokens.

- [ ] **Step 4: Implement the controller contract**

Return `{code, message, traceId, data}`. Return 401 for invalid credentials or refresh sessions, 409 for duplicate registration, 422 for invalid input, and a generic credential error message for login failures. Use the authenticated subject for `/me`.

- [ ] **Step 5: Add controller tests**

Assert JSON field names, 401/409/422 status codes, no password/hash fields in responses, and that logout invalidates the supplied session.

- [ ] **Step 6: Run all auth tests**

Run `mvn -pl services/auth-service test`. Expected: PASS.

- [ ] **Step 7: Commit authentication behavior**

```bash
git add services/auth-service
git commit -m "feat: implement password authentication and token rotation"
```

### Task 3: Enforce JWT authentication at Gateway and secure service identity propagation

**Files:**
- Modify: `services/gateway-service/pom.xml`
- Modify: `services/gateway-service/src/main/resources/application.yml`
- Modify: `services/gateway-service/src/main/java/com/cloudticket/gateway/config/RouteConfiguration.java`
- Create: `services/gateway-service/src/main/java/com/cloudticket/gateway/security/JwtAuthenticationFilter.java`
- Create: `services/gateway-service/src/main/java/com/cloudticket/gateway/security/AnonymousPathPolicy.java`
- Modify: `common/common-web/src/main/java/com/cloudticket/common/web/ApiError.java`
- Create: `services/gateway-service/src/test/java/com/cloudticket/gateway/JwtAuthenticationFilterTest.java`
- Create: `services/gateway-service/src/test/java/com/cloudticket/gateway/AnonymousPathPolicyTest.java`

**Interfaces:**
- Consumes: JWT signing key/public key, auth-service discovery name, existing route and trace filters.
- Produces: authenticated request context with trusted `X-User-Id`, `X-User-Roles`, `X-User-Permissions`, and `X-Scope-Version` headers generated by Gateway; public routes remain anonymous.

- [ ] **Step 1: Write failing Gateway tests**

Test valid token acceptance, expired token rejection, invalid signature rejection, revoked `jti` rejection, malformed or oversized identity headers being discarded, and anonymous access only for `/api/auth/**` and published public activity GET routes.

- [ ] **Step 2: Run focused Gateway tests**

Run `mvn -pl services/gateway-service -Dtest=JwtAuthenticationFilterTest,AnonymousPathPolicyTest test`. Expected: FAIL because the filter and policy are absent.

- [ ] **Step 3: Implement JWT validation and route protection**

Validate signature, `exp`, `iat`, `jti`, and `scopeVersion`. Do not accept identity headers supplied by the client; overwrite them only after token validation. Return a standard 401 error with the existing trace ID.

- [ ] **Step 4: Route auth-service and block internal endpoints**

Add `/api/auth/** -> lb://auth-service`. Keep `/internal/**` unreachable from public Gateway routes; service-to-service requests require an internal credential or signed internal token.

- [ ] **Step 5: Run Gateway and existing tests**

Run `mvn -pl services/gateway-service,common/common-web test`. Expected: PASS.

- [ ] **Step 6: Commit Gateway authentication**

```bash
git add services/gateway-service common/common-web
git commit -m "feat: enforce jwt authentication at gateway"
```

### Task 4: Add multi-activity and immutable seat-layout management

**Files:**
- Modify: `services/activity-service/pom.xml`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/ActivityEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/VenueEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/SessionEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/SeatLayoutVersionEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityAdminController.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityController.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityCommandService.java`
- Create: `services/activity-service/src/main/resources/db/migration/V3__multi_activity_management.sql`
- Create: `services/activity-service/src/main/resources/db/migration/V4__seed_multiple_published_activities.sql`
- Create: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityManagementTest.java`
- Create: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityScopeAuthorizationTest.java`

**Interfaces:**
- Consumes: trusted Gateway identity headers, `activity:*` permissions, and existing Activity query DTOs.
- Produces: public paginated `GET /api/activities`, public `GET /api/activities/{id}`, and administrator CRUD/publish/offline endpoints under `/api/admin/activities`, `/venues`, and `/sessions`.

- [ ] **Step 1: Write failing activity management tests**

Cover two published activities appearing in the public list, draft exclusion, publish validation, immutable layout version rejection, offline hiding without deleting historical records, and an operator being rejected for an activity outside its Scope.

- [ ] **Step 2: Run Activity tests to verify failure**

Run `mvn -pl services/activity-service -Dtest=ActivityManagementTest,ActivityScopeAuthorizationTest test`. Expected: FAIL because management entities, migrations, and commands are absent.

- [ ] **Step 3: Add schema and seed data**

Create normalized activity, venue, session, layout-version, area, row, and seat tables with status indexes and foreign keys. Seed the current demo plus a second published activity with separate venue/session/seat IDs. Do not reuse the demo ID.

- [ ] **Step 4: Implement public queries**

Return only `PUBLISHED` activities and active sessions from public routes. Include stable IDs, venue summary, time window, and layout version metadata. Keep internal inventory seat initialization separate from public DTOs.

- [ ] **Step 5: Implement scoped administrator commands**

Require `activity:write` for edits and `activity:publish` for publishing. Validate venue, layout, price, and session times before publication; publication creates an immutable layout version and emits an inventory initialization command/event.

- [ ] **Step 6: Run Activity tests and existing controller tests**

Run `mvn -pl services/activity-service test`. Expected: PASS.

- [ ] **Step 7: Commit multi-activity support**

```bash
git add services/activity-service
git commit -m "feat: add multi-activity management and publishing"
```

### Task 5: Enforce user ownership and administrator scopes in inventory and order services

**Files:**
- Modify: `services/order-service/src/main/java/com/cloudticket/order/api/OrderController.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/service/OrderCommandService.java`
- Modify: `services/order-service/src/main/resources/db/migration/V1__order_schema.sql`
- Create: `services/order-service/src/main/java/com/cloudticket/order/security/OrderAuthorization.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/InternalInventoryController.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/security/InventoryAuthorization.java`
- Create: `services/order-service/src/test/java/com/cloudticket/order/OrderAuthorizationTest.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryAuthorizationTest.java`

**Interfaces:**
- Consumes: Gateway identity headers, `USER`/administrator permissions, session/activity ownership lookup, and existing order/inventory state machines.
- Produces: owner-only order reads/cancellation, scoped admin order operations, and reason-required inventory lock release/adjustment.

- [ ] **Step 1: Write failing authorization tests**

Test that user A cannot read or cancel user B's order, an `ORDER_ADMIN` can read only an assigned activity, an `INVENTORY_ADMIN` cannot release a lock outside its assigned area, and `AUDITOR` cannot invoke any write operation.

- [ ] **Step 2: Run focused tests**

Run `mvn -pl services/order-service,services/inventory-service -Dtest=OrderAuthorizationTest,InventoryAuthorizationTest test`. Expected: FAIL because authorization policies are absent.

- [ ] **Step 3: Add identity-aware authorization policies**

Derive the current user only from the trusted subject header. For every order query and command, apply owner or scope predicates before loading the resource; return 404 for an unauthorized resource. For inventory adjustments, require a nonblank reason and write an audit event.

- [ ] **Step 4: Protect internal inventory routes**

Require an internal service credential or signed internal token on lock/release endpoints and reject calls carrying only browser identity headers. Preserve idempotent release behavior.

- [ ] **Step 5: Run existing consistency and authorization tests**

Run `mvn -pl services/order-service,services/inventory-service test`. Expected: PASS, including existing state-machine, lock, idempotency, and timeout tests.

- [ ] **Step 6: Commit resource authorization**

```bash
git add services/order-service services/inventory-service
git commit -m "feat: enforce ownership and scoped service authorization"
```

### Task 6: Integrate the Vue/Vite frontend and implement user/admin flows

**Files:**
- Create or integrate into root: `web/package.json`, `web/index.html`, `web/src/main.ts`, `web/src/App.vue`
- Modify or integrate from: `.worktrees/cloud-ticketing-phase1/web/src/api.ts`, `.worktrees/cloud-ticketing-phase1/web/src/types.ts`, `.worktrees/cloud-ticketing-phase1/web/src/styles.css`, `.worktrees/cloud-ticketing-phase1/web/src/seatSelection.ts`
- Create: `web/src/auth/session.ts`
- Create: `web/src/auth/authApi.ts`
- Create: `web/src/router.ts`
- Create: `web/src/views/LoginView.vue`
- Create: `web/src/views/RegisterView.vue`
- Create: `web/src/views/ActivityListView.vue`
- Create: `web/src/views/ActivityDetailView.vue`
- Create: `web/src/views/AccountOrdersView.vue`
- Create: `web/src/views/admin/AdminLayout.vue`
- Create: `web/src/views/admin/ActivityAdminView.vue`
- Create: `web/src/views/admin/OrderAdminView.vue`
- Create: `web/src/views/admin/InventoryAdminView.vue`
- Create: `web/src/views/admin/UserRoleAdminView.vue`
- Create: `web/src/views/admin/AuditLogView.vue`
- Create: `web/src/components/PermissionGate.vue`
- Create: `web/src/__tests__/auth-session.test.ts`
- Create: `web/src/__tests__/activity-list.test.ts`
- Create: `web/src/__tests__/admin-route-guard.test.ts`

**Interfaces:**
- Consumes: public/auth/admin APIs from Tasks 2–5; existing seat-selection interaction and visual styles from the phase-one worktree.
- Produces: login/register/logout, token refresh-once behavior, multi-activity list/detail, user orders, permission-aware admin navigation, and 401/403 UI states.

- [ ] **Step 1: Verify the source worktree before integration**

Run `git -C .worktrees/cloud-ticketing-phase1 diff -- web/src docs/evidence/phase-1-verification.md pom.xml` and preserve the displayed changes. Do not reset, clean, or overwrite the worktree.

- [ ] **Step 2: Add failing frontend tests**

Test that the session store clears on refresh failure, an API request retries once after 401, activity cards render server-provided titles rather than a hard-coded demo title, and an admin route rejects a user without the required permission.

- [ ] **Step 3: Run frontend tests to verify failure**

Run `npm test -- --run` from `web/`. Expected: FAIL until the root Vue/Vite app and session utilities exist.

- [ ] **Step 4: Integrate the existing seat-selection app into root `web/`**

Copy the phase-one frontend structure into the root only after reviewing its diff. Retain seat selection and order UI behavior, then replace fallback-only activity state with API-loaded activity lists and route params. Keep the demo fallback visible only as an explicit development error state, never as a successful production response.

- [ ] **Step 5: Implement auth session and API client**

Keep the access token in memory, call `/api/auth/refresh` once on a 401, queue concurrent requests behind the same refresh promise, and clear session state on refresh failure. Attach `Authorization: Bearer <token>` to authenticated requests; never attach a client-provided role or user ID.

- [ ] **Step 6: Implement public, account, and admin routes**

Use route metadata such as `{ requiresAuth: true, permission: 'activity:publish' }`. Render admin menus from permissions, show a dedicated 403 view for denied routes, and keep backend responses authoritative.

- [ ] **Step 7: Run frontend verification**

Run `npm test -- --run` and `npm run build` from `web/`. Expected: PASS and a production `dist/` build.

- [ ] **Step 8: Commit frontend authentication and management flows**

```bash
git add web
git commit -m "feat: add authenticated multi-activity frontend"
```

### Task 7: Wire Docker Compose, service discovery, and local configuration

**Files:**
- Modify: `docker-compose.yml`
- Modify: `docker/mysql/init.sql`
- Modify: `.env.example`
- Modify: `services/auth-service/src/main/resources/application.yml`
- Modify: `services/gateway-service/src/main/resources/application.yml`
- Modify: `README.md`
- Create: `web/Dockerfile`
- Create: `web/nginx.conf`
- Create: `Dockerfile.auth`
- Create: `Dockerfile.gateway`
- Create: `Dockerfile.activity`
- Create: `Dockerfile.inventory`
- Create: `Dockerfile.order`

**Interfaces:**
- Consumes: auth routes, JWT public-key configuration, MySQL auth schema, and the root Vue/Vite build.
- Produces: reproducible local stack with auth-service, Gateway, four domain services, frontend, MySQL, Redis, Kafka, and Nacos.

- [ ] **Step 1: Add failing configuration checks**

Run `docker compose config` and a script that asserts `auth-service` has a health check, `web` depends on Gateway, and no secret is hard-coded in Compose. Record the current missing-service failure.

- [ ] **Step 2: Add auth database and service registration**

Create `auth_db`, add auth-service to Compose, configure Nacos registration, JWT signing/public key variables, database URL, and cookie/CORS settings from `.env.example`.

- [ ] **Step 3: Add frontend production image**

Build `web` with Node, serve `dist/` using Nginx, proxy `/api/` to Gateway, and configure SPA fallback to `index.html`.

- [ ] **Step 4: Verify Compose configuration**

Run `docker compose config`, then `docker compose build auth-service gateway-service web`. Expected: valid expanded configuration and successful image builds; if an external registry mirror returns 403, record it as an environment limitation rather than changing application behavior.

- [ ] **Step 5: Commit deployment wiring**

```bash
git add docker-compose.yml docker/mysql/init.sql .env.example README.md web services
git commit -m "chore: wire auth and frontend into docker compose"
```

### Task 8: Run security, authorization, end-to-end, and regression verification

**Files:**
- Create: `tests/auth/AuthFlowIT.java`
- Create: `tests/auth/AuthorizationMatrixIT.java`
- Create: `tests/e2e/MultiActivityOrderFlowIT.java`
- Create: `tests/security/TokenReplayIT.java`
- Create: `docs/evidence/auth-rbac-multi-activity-verification.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: the Docker Compose stack and APIs from Tasks 1–7.
- Produces: saved evidence for authentication, scope enforcement, multi-activity isolation, frontend build, and existing seat/order consistency behavior.

- [ ] **Step 1: Write the failing integration scenarios**

Define tests for register/login/refresh/logout, Refresh Token replay, user A reading user B's order, each administrator role against an in-scope and out-of-scope resource, two published activities, and admin publication becoming visible in the public list.

- [ ] **Step 2: Start the local dependencies**

Run `docker compose up -d mysql redis kafka nacos` and wait for each health check. Do not claim a test passed if a dependency is unavailable.

- [ ] **Step 3: Run authentication and authorization tests**

Run `mvn -P e2e -Dtest=AuthFlowIT,AuthorizationMatrixIT,TokenReplayIT verify`. Expected: all authentication and scope cases pass with 401/403/404 behavior as specified.

- [ ] **Step 4: Run the multi-activity order flow**

Run `mvn -P e2e -Dtest=MultiActivityOrderFlowIT verify`. Assert activity/session/seat/order IDs remain isolated across two activities and existing lock, idempotency, timeout, Outbox, and duplicate-event tests still pass.

- [ ] **Step 5: Run frontend checks and Docker configuration**

Run `npm test -- --run`, `npm run build`, and `docker compose config`. Save command output, environment versions, and any unavailable registry/dependency details.

- [ ] **Step 6: Write the evidence report**

Record exact commands, pass/fail results, sample sizes, limitations, and unimplemented Payment/Ticket/Reconciliation boundaries in `docs/evidence/auth-rbac-multi-activity-verification.md`. Do not include unsupported performance or production claims.

- [ ] **Step 7: Commit verification evidence**

```bash
git add tests docs/evidence/auth-rbac-multi-activity-verification.md README.md
git commit -m "test: verify auth rbac and multi-activity flows"
```

## Self-Review Checklist

- Spec coverage: Tasks 1–3 cover authentication and Gateway validation; Tasks 4–5 cover multiple activities and resource authorization; Task 6 covers Vue/Vite user/admin flows; Task 7 covers Docker; Task 8 covers security, integration, and regression evidence.
- Placeholder scan: no `TODO`, `TBD`, “implement later”, or undefined implementation handoff appears in the plan.
- Type consistency: the JWT identity fields, endpoint paths, role names, permission codes, table names, and frontend route metadata are reused consistently across tasks.
- Existing changes: the plan explicitly inspects and preserves `.worktrees/cloud-ticketing-phase1` changes before frontend integration.
- Safety boundary: no task uses `git reset --hard`, `git checkout --`, worktree deletion, or broad file cleanup.

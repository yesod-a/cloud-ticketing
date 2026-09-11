# Cloud Ticketing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working slice of Cloud Ticketing as Spring Cloud microservices: Gateway, Activity, Inventory, and Order, with Redis/Lua seat locking, MySQL-owned data, Kafka events, transactional Outbox, XXL-JOB timeout compensation, and reproducible concurrency/failure tests.

**Architecture:** Use a single Maven multi-module repository with one Spring Boot application per bounded context. Gateway handles routing and request correlation; Activity owns event/session/seat layout data; Inventory owns seat availability and Redis locks; Order owns order state, idempotency, and Outbox. Synchronous HTTP is used for command validation and seat locking; Kafka propagates committed business facts. Each service has its own schema and writes only its own tables.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Cloud 2025.0.x, Spring Cloud Alibaba 2025.0.x, Spring Cloud Gateway, Nacos, OpenFeign, Spring Kafka, Redis, Lua, MySQL 8, MyBatis-Plus, XXL-JOB, Sentinel, Testcontainers, JUnit 5, WireMock, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-11-cloud-ticketing-design.md`

## Global Constraints

- Use Java 21 and Spring Boot 3.5.5 for every service.
- Keep service-owned writes isolated; no service may update another service's tables.
- Use Redis only for temporary concurrency locks; MySQL remains the auditable source of truth.
- Every externally retried command must carry an idempotency key or stable business key.
- Every Kafka event must include `eventId`, `eventType`, `aggregateType`, `aggregateId`, `occurredAt`, `schemaVersion`, `traceId`, and `payload`.
- Do not claim real payment integration, production traffic, zero oversell, throughput, or latency until a recorded test proves it.
- First phase includes Gateway, Activity, Inventory, and Order only; Payment, Ticket, and Reconciliation are extension contracts until the first phase is green.

---

### Task 1: Create the multi-module build and local infrastructure

**Files:**
- Create: `pom.xml`
- Create: `common/common-domain/pom.xml`
- Create: `common/common-web/pom.xml`
- Create: `common/common-events/pom.xml`
- Create: `services/gateway-service/pom.xml`
- Create: `services/activity-service/pom.xml`
- Create: `services/inventory-service/pom.xml`
- Create: `services/order-service/pom.xml`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `README.md`
- Test: `pom.xml` Maven validate and module compilation

**Interfaces:**
- Produces Maven modules consumed by every later service.
- `common-events` exposes immutable event envelope types and JSON serialization conventions.
- `common-web` exposes request correlation ID handling and standard error response shape.

- [ ] **Step 1: Write the failing build check**

Run `mvn -q -DskipTests compile` from the repository root and record the expected failure because the parent POM and modules do not exist.

- [ ] **Step 2: Create the parent and module POMs**

Declare Java 21, Spring Boot 3.5.5 dependency management, Spring Cloud 2025.0.x BOM, Spring Cloud Alibaba 2025.0.x BOM, and modules in this order: common libraries, gateway, activity, inventory, order. Add compiler, surefire, and failsafe plugins with deterministic encoding.

- [ ] **Step 3: Create shared event and web contracts**

Define `EventEnvelope<T>` with the eight required fields, `EventTypes` constants for `OrderCreated`, `OrderExpired`, `InventoryLocked`, and `InventoryReleased`, plus a servlet/webflux-compatible correlation filter that accepts `X-Trace-Id` or generates one and returns it in the response.

- [ ] **Step 4: Add Docker Compose dependencies**

Run Kafka in KRaft mode, Redis, MySQL, and Nacos with health checks. Create four MySQL schemas: `activity_db`, `inventory_db`, `order_db`, and `gateway_db` only if gateway persistence becomes necessary. Keep credentials in `.env.example`, not source files.

- [ ] **Step 5: Run the build check**

Run `mvn -q -DskipTests compile`. Expected: PASS for all modules. Run `docker compose config` to verify variable expansion and service health-check syntax.

- [ ] **Step 6: Commit the foundation**

Commit `chore: bootstrap cloud ticketing multi-module foundation`.

### Task 2: Implement Gateway routing, correlation, and protection

**Files:**
- Create: `services/gateway-service/src/main/java/com/cloudticket/gateway/GatewayApplication.java`
- Create: `services/gateway-service/src/main/resources/application.yml`
- Create: `services/gateway-service/src/main/java/com/cloudticket/gateway/config/RouteConfiguration.java`
- Create: `services/gateway-service/src/main/java/com/cloudticket/gateway/filter/TraceIdGlobalFilter.java`
- Create: `services/gateway-service/src/main/java/com/cloudticket/gateway/config/SentinelConfiguration.java`
- Create: `services/gateway-service/src/test/java/com/cloudticket/gateway/TraceIdGlobalFilterTest.java`

**Interfaces:**
- Routes `/api/activities/**` to Activity and `/api/orders/**` to Order using service discovery names.
- Routes internal Inventory calls only through a private route or direct service URL; public clients cannot invoke internal lock endpoints.
- Preserves or generates `X-Trace-Id` and emits standard JSON errors.

- [ ] **Step 1: Write failing filter tests**

Test that a request without `X-Trace-Id` receives a nonblank response header and a request with a valid header preserves it. Test that a malformed header longer than 64 characters is replaced rather than forwarded.

- [ ] **Step 2: Implement Gateway application and routes**

Configure Spring Cloud Gateway route predicates for the two public prefixes, Nacos discovery locator disabled by default, and explicit service IDs so accidental route exposure is impossible.

- [ ] **Step 3: Implement correlation and basic Sentinel rules**

Add the global filter, standard error body `{code, message, traceId}`, and rate rules for order creation and seat queries. Do not use fallback responses that claim an order was created.

- [ ] **Step 4: Run Gateway tests**

Run `mvn -pl services/gateway-service test`. Expected: PASS, including correlation and malformed-header tests.

- [ ] **Step 5: Commit Gateway**

Commit `feat: add gateway routing and request correlation`.

### Task 3: Implement Activity service and seedable session data

**Files:**
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/ActivityApplication.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/ActivityEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/SessionEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/domain/SeatEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/mapper/*.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityController.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityQueryService.java`
- Create: `services/activity-service/src/main/resources/db/migration/V1__activity_schema.sql`
- Create: `services/activity-service/src/main/resources/db/migration/V2__seed_demo_session.sql`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityControllerTest.java`

**Interfaces:**
- `GET /api/activities/{activityId}` returns activity, sessions, and venue summary.
- `GET /internal/sessions/{sessionId}/seats` returns stable seat IDs and layout metadata for Inventory.
- Produces no order or inventory writes.

- [ ] **Step 1: Write API and repository tests**

Test a seeded session returns a deterministic seat list and an unknown session returns the standard not-found error with `traceId`.

- [ ] **Step 2: Add Flyway schema and entities**

Create tables for activity, venue, session, and seat layout with foreign keys, status columns, and indexes on session/time. Seed one demo activity with at least 20 seats.

- [ ] **Step 3: Implement query endpoints**

Return DTOs rather than persistence entities. Enforce that only published sessions are exposed through public routes; internal seat layout lookup may return inactive layout metadata for Inventory initialization.

- [ ] **Step 4: Run Activity tests**

Run `mvn -pl services/activity-service test`. Expected: PASS with a test database or Testcontainers MySQL.

- [ ] **Step 5: Commit Activity**

Commit `feat: add activity and session query service`.

### Task 4: Implement Inventory Redis/Lua locking and durable lock records

**Files:**
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/InventoryApplication.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/domain/InventorySeatEntity.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/domain/InventoryLockEntity.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/redis/SeatLockScript.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/service/InventoryLockService.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/InternalInventoryController.java`
- Create: `services/inventory-service/src/main/resources/scripts/lock-seats.lua`
- Create: `services/inventory-service/src/main/resources/scripts/release-seats.lua`
- Create: `services/inventory-service/src/main/resources/db/migration/V1__inventory_schema.sql`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryLockServiceTest.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryLockConcurrencyIT.java`

**Interfaces:**
- `POST /internal/inventory/locks` accepts `orderId`, `sessionId`, `seatIds`, `lockToken`, and `ttlSeconds`; returns all-or-nothing success.
- `POST /internal/inventory/locks/{orderId}/release` is idempotent.
- `GET /internal/inventory/sessions/{sessionId}/seats` exposes current durable status for diagnostics.

- [ ] **Step 1: Write failing Lua behavior tests**

Test that locking two free seats succeeds and writes both keys with one TTL; locking when one key belongs to another order changes none of the keys; repeating the same order and token is idempotent; release only deletes keys owned by the matching token.

- [ ] **Step 2: Implement Redis scripts**

Write `lock-seats.lua` to validate all keys before writing any key, set `orderId:lockToken` values and a common TTL, and return a numeric result plus conflicting seat. Write `release-seats.lua` to delete only matching values.

- [ ] **Step 3: Add durable inventory lock transaction**

Persist `inventory_lock` rows with order, session, seat, token, expiry, and status. Use unique constraints for one active lock per `(session_id, seat_id)` and conditional updates for release. Redis success followed by database failure must invoke release and expose a compensatable error.

- [ ] **Step 4: Implement internal endpoints and event records**

Return a stable error code for conflict, Redis unavailable, and invalid seat. Record `InventoryLocked` or `InventoryReleased` in the service Outbox only after the durable transaction succeeds.

- [ ] **Step 5: Run unit and integration tests**

Run `mvn -pl services/inventory-service test`. The concurrency integration test must launch at least 100 lock attempts for one seat and assert exactly one success and one active durable lock. Use Testcontainers Redis and MySQL.

- [ ] **Step 6: Commit Inventory**

Commit `feat: add atomic seat locking with redis lua`.

### Task 5: Implement Order idempotency, state machine, and Outbox publishing

**Files:**
- Create: `services/order-service/src/main/java/com/cloudticket/order/OrderApplication.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/domain/OrderEntity.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/domain/OrderItemEntity.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/domain/IdempotencyRecordEntity.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/domain/OutboxEventEntity.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/service/OrderCommandService.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/service/OrderStateMachine.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/service/OrderTimeoutJob.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/messaging/OutboxPublisher.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/api/OrderController.java`
- Create: `services/order-service/src/main/resources/db/migration/V1__order_schema.sql`
- Create: `services/order-service/src/test/java/com/cloudticket/order/OrderCommandServiceTest.java`
- Create: `services/order-service/src/test/java/com/cloudticket/order/OrderStateMachineTest.java`
- Create: `services/order-service/src/test/java/com/cloudticket/order/OrderIdempotencyIT.java`

**Interfaces:**
- `POST /api/orders` accepts `sessionId`, `seatIds`, and `idempotencyKey`; returns one order representation for repeated identical requests.
- `GET /api/orders/{orderId}` returns state, expiry, seats, and trace ID.
- `POST /internal/orders/{orderId}/expire` performs conditional `PENDING_PAYMENT -> EXPIRED` and publishes `OrderExpired` only when the update succeeds.
- `OutboxPublisher` reads unpublished events, sends Kafka records keyed by `aggregateId`, and records publish attempts.

- [ ] **Step 1: Write failing state and idempotency tests**

Test all allowed transitions, reject `EXPIRED -> PAID`, return the original order for the same idempotency key and request digest, and return conflict for the same key with a different digest.

- [ ] **Step 2: Implement order schema and state machine**

Create order, item, idempotency, and outbox tables. Implement conditional state updates and a single transaction that stores the order, request record, and `OrderCreated` event after Inventory lock success.

- [ ] **Step 3: Implement Inventory Feign client and command flow**

Call Inventory with a generated lock token. On Inventory conflict return a business error without creating an order. If order persistence fails after a successful lock, call release and emit an alert-level log with `orderId` and `traceId`.

- [ ] **Step 4: Implement Kafka Outbox publisher and consumer idempotency table**

Configure a producer with idempotence enabled, stable JSON envelope serialization, and bounded retry. Add a consumer-side `processed_event` table contract even before Payment/Ticket exists so later consumers follow the same pattern.

- [ ] **Step 5: Implement XXL-JOB timeout scan**

Scan a bounded page of expired `PENDING_PAYMENT` orders, conditionally transition each order, and publish `OrderExpired` only for rows updated by the current run. Add a job lock or deterministic partition parameter so two job executions do not double-process.

- [ ] **Step 6: Run Order tests**

Run `mvn -pl services/order-service test`. The integration test must repeat the same request concurrently and assert one order ID, one successful Inventory lock, one `OrderCreated` outbox row, and no duplicate order items.

- [ ] **Step 7: Commit Order**

Commit `feat: add idempotent order workflow and kafka outbox`.

### Task 6: Wire the first end-to-end happy path

**Files:**
- Modify: `services/gateway-service/src/main/resources/application.yml`
- Modify: `services/activity-service/src/main/resources/application.yml`
- Modify: `services/inventory-service/src/main/resources/application.yml`
- Modify: `services/order-service/src/main/resources/application.yml`
- Create: `tests/e2e/order-flow.http`
- Create: `tests/e2e/OrderFlowIT.java`
- Modify: `docker-compose.yml`
- Modify: `README.md`

**Interfaces:**
- Public flow: Gateway -> Activity query -> Order create -> Inventory lock -> Order response.
- Timeout flow: Order job -> `OrderExpired` event -> Inventory release.

- [ ] **Step 1: Write the failing end-to-end test**

Start dependencies with `docker compose up -d`, start four services under a test profile, submit an order through Gateway, and assert the returned order is `PENDING_PAYMENT` with an expiry timestamp and locked seats.

- [ ] **Step 2: Configure service discovery and Feign**

Register services in Nacos, configure Gateway route IDs, configure Order's Inventory client URL/discovery name, and propagate `X-Trace-Id` through Feign headers.

- [ ] **Step 3: Wire timeout release**

Use a short test payment window, run the timeout job, consume the event, and assert order `EXPIRED`, inventory lock `RELEASED`, and Redis keys absent.

- [ ] **Step 4: Run the end-to-end test**

Run `mvn -P e2e verify`. Expected: PASS with service logs showing one trace ID across Gateway, Order, Inventory, Outbox, Kafka, and timeout release.

- [ ] **Step 5: Commit the first vertical slice**

Commit `feat: wire end-to-end seat reservation flow`.

### Task 7: Add failure, duplicate, and concurrency evidence

**Files:**
- Create: `tests/failure/OutboxRecoveryIT.java`
- Create: `tests/failure/DuplicateEventIT.java`
- Create: `tests/failure/PaymentTimeoutRaceIT.java`
- Create: `tests/load/SeatLockLoadTest.java`
- Create: `docs/evidence/phase-1-verification.md`
- Modify: `README.md`

**Interfaces:**
- Produces reproducible evidence for the résumé claims; no business behavior should be changed only to make a test look better.

- [ ] **Step 1: Write Outbox recovery test**

Stop or block Kafka after order commit, assert the order remains queryable and the Outbox row is unpublished, restore Kafka, run the publisher, and assert one event is eventually marked published.

- [ ] **Step 2: Write duplicate event test**

Deliver the same event twice to a consumer harness and assert the processed-event uniqueness rule prevents a second state mutation.

- [ ] **Step 3: Write timeout/payment race test**

Run payment confirmation and expiry transition concurrently and assert exactly one legal terminal path wins; the losing request returns the current state without a second side effect.

- [ ] **Step 4: Write the seat-lock load test**

Run 100 concurrent requests against one seat and a separate run against 100 distinct seats. Record success count, conflict count, p50/p95 lock latency, and database active-lock count. Do not copy numbers into the résumé until the run is saved.

- [ ] **Step 5: Document evidence and gaps**

Write exact commands, environment, sample size, output, and limitations to `docs/evidence/phase-1-verification.md`. Mark real payment, Ticket, and Reconciliation as not implemented.

- [ ] **Step 6: Run all checks**

Run `mvn verify`, `docker compose config`, and the load test profile. Expected: unit, integration, contract, failure, and concurrency tests pass; any unavailable Docker dependency is reported rather than hidden.

- [ ] **Step 7: Commit verification evidence**

Commit `test: add phase one concurrency and failure evidence`.

### Task 8: Prepare the extension contracts without claiming implementation

**Files:**
- Create: `docs/contracts/payment-events-v1.md`
- Create: `docs/contracts/ticket-events-v1.md`
- Create: `docs/contracts/reconciliation-rules-v1.md`
- Modify: `docs/superpowers/specs/2026-09-11-cloud-ticketing-design.md`
- Modify: `README.md`

**Interfaces:**
- Documents only; no Payment, Ticket, or Reconciliation runtime code is added in phase one.

- [ ] **Step 1: Define payment event compatibility**

Document `PaymentSucceeded` and `PaymentFailed` payloads, allowed Order transitions, idempotency requirements, and schema versioning.

- [ ] **Step 2: Define ticket issuance compatibility**

Document one-ticket-per-order uniqueness, `TicketIssued`/`TicketIssueFailed`, and the consumer recovery path.

- [ ] **Step 3: Define reconciliation rules**

Document authoritative fields, mismatch classes, automatic repair limits, and manual-review conditions.

- [ ] **Step 4: Verify scope language**

Search documentation for claims of implemented payment, ticketing, production scale, or zero oversell and remove any unsupported claim.

- [ ] **Step 5: Commit extension contracts**

Commit `docs: define future payment ticket and reconciliation contracts`.

# Redis Inventory Cache Preheat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Make inventory seat reads Redis-first with timed, idempotent preheating through XXL-JOB, while preserving MySQL as the durable source of truth and retaining cold-start fallback.

**Architecture:** Activity Service stores an explicit `sale_start_at` on each session. Inventory Service owns a durable warmup-job row, builds versioned layout/Bitmap projections from its own MySQL tables, and exposes Redis-first public reads. An XXL-JOB handler claims due rows and dispatches bounded workers; read misses use a per-session Redis rebuild lease and MySQL fallback. Existing Redis reservation/hold keys and MySQL lock transitions remain authoritative for writes.

**Tech Stack:** Spring Boot 3.5, Java 21, MyBatis-Plus, Flyway, Redis/StringRedisTemplate, XXL-JOB executor, JUnit 5/Mockito.

**Spec:** `docs/superpowers/specs/2026-10-07-redis-inventory-preheat-design.md`

## Global Constraints

- Redis is a rebuildable projection; MySQL remains the durable inventory fact.
- Read paths never create business seat holds.
- Versioned Redis writes set `ready/current-version` only after all layout and Bitmap keys are complete.
- Existing direct-lock and queued-reservation feature flags remain backward compatible and default-safe.
- Every production behavior change is preceded by a failing test.

---

### Task 1: Persist sale start and warmup jobs

**Files:**
- Modify: `services/activity-service/src/main/resources/db/migration/V14__session_sale_start.sql`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/entity/SessionEntity.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/domain/Session.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/command/SessionCommands.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/SessionRepository.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/service/SessionService.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/client/InventoryCacheWarmupClient.java`
- Modify: `services/activity-service/src/test/java/com/cloudticket/activity/SessionServiceTest.java`
- Create: `services/inventory-service/src/main/resources/db/migration/V16__inventory_cache_warmup.sql`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/entity/InventoryCacheWarmupEntity.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/mapper/InventoryCacheWarmupMapper.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/InventoryCacheWarmupRepository.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/InternalCacheWarmupController.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryCacheWarmupRepositoryTest.java`

**Interfaces:**
- Session create/update commands accept nullable ISO `saleStartAt`.
- `InventoryCacheWarmupRepository.upsert(sessionId, saleStartAt)` creates `preheatAt = saleStartAt - configured lead` and resets a changed schedule to `PENDING`.
- `POST /api/internal/inventory/cache-warmups` is the internal idempotent boundary Activity Service uses after session create/update/publish.
- Repository exposes `claimDue(workerId, now, leaseUntil, limit)`, `markReady`, `markFailed`, and `releaseExpiredClaims`.

- [ ] **Step 1: Write the failing tests** for sale-start propagation, preheat calculation, due claiming, duplicate claim rejection, and retry state.
- [ ] **Step 2: Run the focused tests** and verify they fail because the field/table/repository behavior is absent.
- [ ] **Step 3: Add Flyway migrations and minimal entity/mapper/repository implementation.** Use conditional updates for claims and store bounded error text.
- [ ] **Step 4: Thread `saleStartAt` through activity session commands, domain records, repository mappings, and service calls without changing existing null behavior.**
- [ ] **Step 5: Add the internal warmup client and Inventory endpoint; call it after Activity session create/update/publish commits, with idempotent retry-safe request semantics.**
- [ ] **Step 6: Run focused activity/inventory tests and verify all pass.**

### Task 2: Build versioned Redis layout projection

**Files:**
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/cache/InventoryLayoutProjection.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/cache/SeatBitmapProjection.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryLayoutProjectionTest.java`

**Interfaces:**
- `ProjectionSnapshot rebuildSnapshot(String sessionId, List<InventorySeatEntity> source, String version)` builds immutable layout JSON plus sold/locked/disabled bytes.
- `void writeVersioned(String sessionId, ProjectionSnapshot snapshot)` writes all versioned keys, then current-version and ready.
- `Optional<ProjectionSnapshot> readReady(String sessionId)` reads layout and Bitmaps using one Redis pipeline.
- `void invalidate(String sessionId)` clears only the ready/current pointer.

- [ ] **Step 1: Write failing tests** for complete versioned writes, ready-last ordering, missing/incomplete version rejection, and layout/status reconstruction.
- [ ] **Step 2: Run the focused tests and verify expected failures.**
- [ ] **Step 3: Implement the projection service using the existing session hash-tag convention and StringRedisTemplate callbacks/pipeline.**
- [ ] **Step 4: Refactor existing Bitmap rebuild/overlay code to reuse the projection’s snapshot construction without changing current fallback behavior.**
- [ ] **Step 5: Run projection tests and the existing seat projection test suite.**

### Task 3: Add XXL-JOB preheat executor and bounded worker

**Files:**
- Modify: `services/inventory-service/pom.xml`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/config/XxlJobConfiguration.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/cache/InventoryCachePreheatService.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/job/InventoryCachePreheatJob.java`
- Modify: `services/inventory-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryCachePreheatServiceTest.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryCachePreheatJobTest.java`

**Interfaces:**
- `int preheatDue(String triggerId, int limit)` claims due jobs, submits bounded per-session work, and returns the claimed count.
- `void preheatSession(String sessionId, String workerId)` acquires the per-session rebuild lease, reads durable inventory rows, writes a versioned projection, and marks the durable task ready/failed.
- XXL-JOB handler name: `inventoryCachePreheatJobHandler`; default cron/configuration is externalized and disabled unless an XXL-JOB admin address is supplied.

- [ ] **Step 1: Write failing tests** for successful preheat, idempotent rerun, failed retry state, expired claim recovery, and executor bounded submission.
- [ ] **Step 2: Run focused tests and verify failures.**
- [ ] **Step 3: Add the pinned XXL-JOB core dependency and conditional executor configuration.** Keep application startup safe when the feature is disabled.
- [ ] **Step 4: Implement the preheat service with a bounded `ThreadPoolTaskExecutor`, durable claim lease, Redis rebuild lease, and projection write.**
- [ ] **Step 5: Implement the XXL-JOB handler and configuration properties for lead time, batch size, worker count, lease, retry delay, and enablement.**
- [ ] **Step 6: Run focused tests and compile inventory-service.**

### Task 4: Make public seat reads Redis-first with cold-start single flight

**Files:**
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/PublicSeatController.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/cache/InventoryReadService.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryReadServiceTest.java`
- Modify: `services/inventory-service/src/test/java/com/cloudticket/inventory/PublicSeatControllerTest.java`

**Interfaces:**
- `List<InventorySeatEntity> seats(String sessionId)` reads a ready Redis snapshot, otherwise acquires a short Redis rebuild lock, rebuilds once from MySQL, and falls back to MySQL if Redis is unavailable.

- [ ] **Step 1: Write failing tests** for Redis hit without MySQL list access, miss single-flight, incomplete-version fallback, and Redis outage fallback.
- [ ] **Step 2: Run the focused tests and verify failures.**
- [ ] **Step 3: Implement `InventoryReadService` using the projection service, short rebuild lease, double-check-after-lock, and bounded fallback.**
- [ ] **Step 4: Switch `PublicSeatController` to the read service while preserving the existing public response shape.**
- [ ] **Step 5: Run inventory API and projection tests.**

### Task 5: Keep projections current after inventory writes

**Files:**
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/service/InventoryReservationService.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/InternalSeatProvisionController.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/reconcile/SeatProjectionRebuildScheduler.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryProjectionConsistencyTest.java`

- [ ] **Step 1: Write failing tests** proving provision, lock, release, and confirm invalidate or refresh the right session projection and never mark partial versions ready.
- [ ] **Step 2: Run the focused tests and verify failures.**
- [ ] **Step 3: Add projection invalidation/rebuild hooks after durable inventory changes, preserving Redis hold release semantics.**
- [ ] **Step 4: Update reconciliation to rebuild layout and status projection when ready/version is missing, without deleting active hold keys.**
- [ ] **Step 5: Run inventory service tests.**

### Task 6: End-to-end verification and operational configuration

**Files:**
- Modify: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityControllerTest.java`
- Modify: `services/inventory-service/src/test/java/com/cloudticket/inventory/GeneralAdmissionSessionTest.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/InventoryCachePreheatIntegrationTest.java`
- Modify: `README.md`
- Modify: `docs/evidence/redis-seat-migration-verification.md`

- [ ] **Step 1: Write failing integration tests** for sale-start scheduling, Redis-first seat reads, cold-start rebuild, direct lock correctness, queued reservation compatibility, and expiry/payment state changes.
- [ ] **Step 2: Run the focused integration tests and verify failures.**
- [ ] **Step 3: Implement only the configuration/test fixtures needed to make the tests pass.**
- [ ] **Step 4: Run the complete Maven test suite and inventory/activity builds.**
- [ ] **Step 5: Record the verified flags, fallback behavior, and remaining external XXL-JOB admin prerequisite in the evidence document.**

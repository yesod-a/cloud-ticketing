# Redis Seat Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Gradually add Redis Bitmap seat projections, feature-gated Lua seat locking, and reconciliation evidence without removing MySQL inventory truth.

**Architecture:** Phase 1 adds a read-through Bitmap projection with MySQL fallback and invalidation. Phase 2 adds a disabled-by-default Redis Lua lock path with tokenized TTL holds and durable MySQL lock rows. Phase 3 adds projection reconciliation and repeatable benchmark/failure evidence; all Redis writes are guarded by feature flags.

**Tech Stack:** Java 21, Spring Boot, Spring Data Redis, MyBatis-Plus, MySQL, Redis Lua, JUnit 5, Docker Compose, PowerShell/JMeter.

**Spec:** `docs/superpowers/specs/2026-09-27-redis-seat-migration-design.md`

## Global Constraints

- MySQL remains the durable inventory and order fact source.
- Redis failures must not create a sellable order without a durable lock decision.
- Phase 2 is disabled by default until concurrency and recovery tests pass.
- Seat indexes are immutable per session after inventory is published.
- Every performance result records environment, workload, baseline, and cache/lock mode.

### Task 1: Phase-1 Bitmap projection primitives

**Files:**
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/cache/SeatBitmapProjection.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/SeatBitmapProjectionTest.java`
- Modify: `services/inventory-service/src/main/resources/application.yml`
- Modify: `services/inventory-service/pom.xml`

**Interfaces:**
- `SeatBitmapProjection.read(String sessionId, List<InventorySeatEntity> source)` returns a list whose status fields are overlaid from Redis when a ready projection exists.
- `SeatBitmapProjection.invalidate(String sessionId)` removes projection keys.
- `SeatBitmapProjection.rebuild(String sessionId, List<InventorySeatEntity> source)` writes sold/locked/disabled bitmaps and a version marker.

- [ ] Write tests for rebuild/read overlay, miss fallback, invalidation, and Redis-disabled behavior.
- [ ] Run the focused test and verify it fails because the projection class is absent.
- [ ] Implement the minimal projection with Redis binary GET/SETBIT access and a MySQL-source fallback.
- [ ] Run focused tests and then the inventory module tests.

### Task 2: Phase-1 public seat read integration

**Files:**
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/PublicSeatController.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/InventorySeatRepository.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/service/InventoryReservationService.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/InternalInventoryController.java`
- Test: `services/inventory-service/src/test/java/com/cloudticket/inventory/PublicSeatControllerTest.java`

**Interfaces:**
- Public reads use the projection and preserve the existing response shape.
- Inventory mutations invalidate the affected session projection after successful database changes.

- [ ] Add failing controller/service tests proving the public response still contains metadata while status comes from the projection.
- [ ] Run tests to verify the expected missing-bean or integration failure.
- [ ] Wire the projection into public reads and mutation invalidation without changing MySQL lock semantics.
- [ ] Run focused tests, Maven inventory tests, and `docker compose config -q`.

### Task 3: Phase-1 baseline and cache benchmark

**Files:**
- Create: `jmeter/RESULTS-seat-bitmap.md`
- Create: `jmeter/scripts/run-seat-read-comparison.ps1`
- Test: `tests/load/SeatReadLoadTest.ps1`

- [ ] Add a read workload for the same session and seat population in DB-only and Bitmap modes.
- [ ] Run the DB-only baseline and save raw summary, environment, and MySQL counters.
- [ ] Run the Bitmap mode with the same workload and save raw summary and Redis memory.
- [ ] Calculate QPS, p50/p95/p99, hit rate, DB query reduction, and error rate; do not claim improvement without both runs.

### Task 4: Phase-2 Lua lock path

**Files:**
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/redis/RedisSeatLockService.java`
- Create: `services/inventory-service/src/main/resources/redis/lock-seats.lua`
- Create: `services/inventory-service/src/main/resources/redis/release-seats.lua`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/RedisSeatLockServiceTest.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/service/InventoryReservationService.java`
- Modify: `services/inventory-service/src/main/resources/application.yml`

- [ ] Write failing tests for all-or-none multi-seat reserve, conflict, token-checked release, and disabled mode.
- [ ] Run the focused tests and verify they fail before implementation.
- [ ] Implement Lua scripts with same-session hash tags, bounded TTL, duplicate/index validation, and token checking.
- [ ] Keep the feature disabled by default; use Redis reserve followed by durable lock insertion and compensation on database failure.
- [ ] Run unit tests, Redis integration tests against Compose, and the 100-concurrent-seat probe.

### Task 5: Phase-3 reconciliation and metrics

**Files:**
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/reconcile/SeatProjectionReconciler.java`
- Create: `services/inventory-service/src/test/java/com/cloudticket/inventory/SeatProjectionReconcilerTest.java`
- Create: `docs/evidence/redis-seat-migration-verification.md`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/OutboxPublisher.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/persistence/mapper/OrderOutboxMapper.java`

- [ ] Write failing tests for rebuilding missing bitmaps, removing orphan holds only when safe, and detecting paid/locked conflicts.
- [ ] Run focused tests to verify the expected failures.
- [ ] Implement bounded reconciliation and metrics without automatic repair of ambiguous financial states.
- [ ] Add Outbox claim/lease and retry backoff tests before changing publisher SQL.
- [ ] Run full Maven/Vitest/Compose regression and failure-injection scenarios.
- [ ] Record baseline, phase-1, and phase-2 measurements with exact commands and limitations.

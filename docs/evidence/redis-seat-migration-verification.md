# Redis Seat Migration Verification

## Scope

This report tracks the three-stage migration in the current checkout. MySQL remains the durable
inventory source of truth. Redis Bitmap and Lua holds are feature-gated and can be disabled without
changing the public API.

## Implemented checkpoints

| Stage | Evidence | Current status |
|---|---|---|
| 1. Bitmap read projection | `SeatBitmapProjectionTest`, `PublicSeatControllerTest`, `SeatProjectionInvalidationTest` | Implemented; MySQL fallback retained; packed bitmap GET/SET |
| 2. Lua temporary holds | `RedisSeatLockServiceTest`, `InventoryReservationServiceTest` | Implemented; stable `seat_index`, sold/locked/disabled checks, disabled by default |
| 3. Reconciliation contract | `InventoryEventConsumerTest`, `SeatProjectionRebuildSchedulerTest`, `SeatProjectionReconcilerTest`, Outbox publisher tests | Kafka consumer, scheduled MySQL-to-Redis repair/reporting, Outbox claim/lease and retry backoff implemented; runtime flags remain off by default |

## Required benchmark runs

Run the same session, seat population, thread count, duration, and environment for each mode:

```text
DB-only:      CLOUDTICKET_SEAT_BITMAP_ENABLED=false CLOUDTICKET_REDIS_SEAT_LOCK_ENABLED=false
Bitmap read:  CLOUDTICKET_SEAT_BITMAP_ENABLED=true  CLOUDTICKET_REDIS_SEAT_LOCK_ENABLED=false
Lua lock:     CLOUDTICKET_SEAT_BITMAP_ENABLED=true  CLOUDTICKET_REDIS_SEAT_LOCK_ENABLED=true
```

Record QPS, p50/p95/p99, error rate, Redis hit rate, MySQL SELECT/UPDATE/INSERT counts, Redis
memory, command latency, MySQL CPU/lock waits, and the exact commit/configuration. A number is not
reported as an improvement until both the DB-only baseline and the corresponding migrated run are
captured from the same environment.

## Current limitations

- Existing sessions require Flyway `V7__inventory_seat_index.sql` before the Redis lock flag is
  enabled; the migration assigns immutable indexes and adds a uniqueness constraint per session.
- Runtime flags `CLOUDTICKET_INVENTORY_EVENTS_ENABLED` and
  `CLOUDTICKET_INVENTORY_RECONCILIATION_ENABLED` remain off by default. They have unit coverage but
  have not yet been enabled together against the rebuilt Compose services.
- Orphan Redis holds are reported but not automatically deleted. The Redis-first/MySQL-second lock
  sequence creates an in-flight window where a single scan could mistake a valid pending reservation
  for an orphan; automated cleanup needs a persisted grace-period confirmation pass.
- Kafka pause/recovery and database/Redis failure-injection runs are not yet complete, so no
  production performance gain is claimed here.

## Latest verification (2026-09-27, Asia/Shanghai)

- `mvn -pl services/inventory-service -am test`: BUILD SUCCESS, 48 inventory tests passed;
  shared-module tests also passed.
- `mvn -pl services/order-service -am test`: BUILD SUCCESS, 31 order tests passed;
  shared-module tests also passed.
- `mvn -pl services/inventory-service,services/order-service -am package -DskipTests`:
  BUILD SUCCESS.
- `docker compose config -q` and `git diff --check`: passed (the latter reports only existing
  CRLF normalization warnings).
- Rebuilt Compose services started successfully. Inventory Flyway applied V7 and V8; Order Flyway
  validated 10 migrations and applied V9 and V10. The Outbox migrations were renumbered from the
  conflicting V5/V6 names so they do not collide with the existing order schema history.
- Compose now injects `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` and exposes the four migration flags;
  defaults remain Bitmap on, Lua lock/events/reconciliation off.

No DB-only versus Bitmap/Lua load-test pair has been collected yet: `JMETER_HOME` is not configured
on this machine and the running services had to be rebuilt first. The table in
`jmeter/RESULTS-seat-bitmap.md` therefore remains `TBD`; no latency or QPS improvement is reported.

# Redis Inventory Cache Preheat Design

## Goal

Make hot inventory reads Redis-first without making Redis the durable inventory fact. Scene seat metadata and mutable availability projections are preheated before the configured sale start, rebuilt on cache misses, and kept recoverable from inventory MySQL.

## Scope

- Add an explicit per-session sale start time and durable cache-preheat state.
- Use XXL-JOB as the multi-instance scheduler for due preheat work, with idempotent task claiming and bounded execution.
- Cache immutable session seat layout plus sold/locked/disabled Bitmap projections in Redis.
- Serve public seat reads from Redis when a complete version is ready; retain MySQL fallback and single-flight rebuild.
- Preserve MySQL as the source of truth for inventory locks, sold seats, admission tickets, and audit/recovery.
- Keep Redis Lua reservation and queued-sale behavior compatible with existing keys and feature flags.

## Non-goals

- Do not move order or inventory final state exclusively into Redis.
- Do not introduce a real payment or ticket service.
- Do not preheat every activity at creation time.
- Do not acquire a business seat hold for a read-only seat query.

## Data model

`activity_session` gains `sale_start_at TIMESTAMP NULL`. Existing sessions without this value keep current behavior and are not scheduled for timed preheat.

Inventory gains a durable `inventory_cache_warmup` table keyed by `session_id`, containing `preheat_at`, `status`, `cache_version`, `attempts`, `next_attempt_at`, `last_error`, `claimed_by`, `claim_until`, `preheated_at`, `created_at`, and `updated_at`. Status values are `PENDING`, `RUNNING`, `READY`, and `FAILED`.

The due query is `preheat_at <= now AND status <> 'READY'`, optionally constrained by `next_attempt_at`, never a fixed time window that can permanently lose a failed task.

## Redis layout

For a session tag `{sessionId}`:

```text
cloudticket:inventory:{sessionId}:layout:{version}
cloudticket:inventory:{sessionId}:sold:{version}
cloudticket:inventory:{sessionId}:locked:{version}
cloudticket:inventory:{sessionId}:disabled:{version}
cloudticket:inventory:{sessionId}:current-version
cloudticket:inventory:{sessionId}:ready
cloudticket:inventory:{sessionId}:rebuild-lock
```

`layout:{version}` is one serialized immutable seat-layout payload indexed by `seatIndex`; the three Bitmap keys contain mutable states. A new version is written to temporary/versioned keys first, and `current-version` plus `ready` are written last. Readers never use an incomplete version.

Existing temporary hold, queued reservation, pending/inflight, idempotency, and general-admission counter keys remain unchanged. Their TTL and Lua ownership rules continue to apply.

## Preheat flow

1. When a session is created or its sale start is configured, Activity Service persists `sale_start_at` and creates/updates the inventory warmup row after the session transaction commits.
2. XXL-JOB runs the due scanner every 30 minutes by default. It loads rows with `preheat_at <= now` and claims each row with a conditional update and a lease. A configurable bounded executor processes claimed sessions.
3. The worker reads the durable inventory rows, serializes the layout, builds the three Bitmaps, and writes all versioned Redis keys in a pipeline. It marks the cache ready only after the writes complete.
4. Success transitions the row to `READY`. Failure increments `attempts`, stores a bounded error message, calculates `next_attempt_at`, and returns it to `FAILED`; the next XXL-JOB run retries it.
5. A per-session Redis `rebuild-lock` prevents duplicate cold-start rebuilds when a read misses outside the scheduled path. The worker double-checks `current-version` after acquiring the lock.

XXL-JOB dispatch prevents normal duplicate scheduling, but durable conditional claiming and idempotent writes remain mandatory for executor crashes, retries, and manual reruns.

## Read flow

`GET /api/sessions/{sessionId}/seats` first reads the ready layout and Bitmaps from Redis and assembles public seat views without a MySQL list query. If Redis is unavailable, the version is incomplete, or the cache is missing, one caller rebuilds under `rebuild-lock`; other callers briefly retry and then fall back to MySQL. A cache read is display-only and may be briefly stale.

General-admission remaining capacity uses Redis counters in queued mode and keeps MySQL fallback for direct mode until the counter projection is enabled for all sessions.

## Write and consistency flow

The order path still performs the authoritative inventory operation. With Redis seat locking enabled, Lua performs the fast atomic admission/hold check, then MySQL conditionally changes `AVAILABLE -> LOCKED` and writes `inventory_lock`; a MySQL failure releases the Redis hold. With the flag disabled, the existing MySQL-only conditional path remains available.

After MySQL commit, inventory events or the existing projection invalidation update/rebuild the Redis status projection. Payment confirmation, cancellation, expiry, and refund remain idempotent transitions. Redis is never used to infer a paid or sold state when the durable MySQL state is ambiguous.

## Recovery rules

- Redis loss: rebuild layout and Bitmaps from `inventory_seat` and active locks; do not recreate orders from Redis.
- Redis read failure: public reads may fall back to MySQL; new Redis-dependent queued reservations fail closed.
- Redis hold without a durable lock: reconciliation keeps it for a grace period, then removes it only after verifying no active MySQL lock/order exists.
- Durable active lock without a Redis hold: reconciliation restores the hold with the remaining TTL when safe.
- Bitmap/version mismatch: MySQL wins and the projection is rebuilt.
- XXL-JOB or worker crash: claim lease expires and the durable warmup row is retried.

## Verification

- Unit tests for due selection, conditional claim, retry scheduling, versioned ready writes, and idempotent reruns.
- Redis integration tests for layout/Bitmap reads, missing-cache single-flight rebuild, Redis failure fallback, and incomplete-version rejection.
- Inventory concurrency tests proving public reads do not create holds and reservation still admits at most one winner.
- End-to-end test for sale-start preheat, cold-start recovery, payment confirmation, expiry release, and MySQL-to-Redis rebuild.
- Operational metrics: preheat success/failure, cache hit/miss, rebuild latency, DB fallback count, Redis command latency, stale-version count, and warmup backlog.

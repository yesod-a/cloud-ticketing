# Queued Ticket Reservation Design

## Goal

Add a selectable `QUEUED` sale mode for hot sessions while preserving the existing `DIRECT` synchronous order flow. The queued path must reserve inventory in Redis without sending every request to MySQL, durably record the publish intent atomically with the Redis reservation, publish commands to Kafka asynchronously, persist inventory holds idempotently, create pending orders, and reconcile failures. Both seated sessions (`GRID`/`ROWS`) and numbered general admission sessions use the same reservation lifecycle.

## State and Invariants

Reservation states are `PENDING_PUBLISH`, `PUBLISHING`, `PUBLISHED`, `INVENTORY_HELD`, `ORDER_CREATED`, `PAID`, `RELEASED`, and `FAILED`. Seats/tickets remain `HELD` before payment and transition to `SOLD` only after an accepted payment success. Expiry/cancel competes with payment through conditional order state transitions; only the winner emits the corresponding event.

Inventory conservation is `capacity = available + held + sold + disabled` for seats and `capacity = available + reserved + sold` for general admission. Every command and transition is keyed by a stable `reservationId`; repeated requests/events must not change counters twice. Redis is the admission-time concurrency authority and MySQL is the durable audit/business record. Redis state can be rebuilt from MySQL plus replayable command records.

## Request and Redis Admission

The public queued request validates authenticated user, session mode, purchase limit, request shape and idempotency key, then invokes one Lua script. The Lua script verifies the idempotency mapping, validates all requested seats or quantity, changes available/held counters or seat holds atomically, allocates monotonic non-reused general-admission ticket numbers, writes a reservation hash containing the complete command payload and expiry, and inserts the reservation into a pending-publish ZSET. The idempotency mapping and reservation record are written in the same script. The API returns `202 Accepted` with `reservationId`; clients query reservation/order status.

All keys touched by one Lua invocation use a common Redis Cluster hash tag per session. Seated reservations validate every selected seat before modifying any seat key. A seat request is all-or-nothing regardless of count. General admission allocates numbers at hold time; released numbers are not reused.

## Kafka Publication and Consumption

A publisher claims due pending ZSET entries using an expiring lease, marks them `PUBLISHING`, and sends a versioned `ReserveCommand` keyed by `sessionId`. Kafka delivery is at least once. On acknowledged send, Redis moves the entry to `PUBLISHED`; on failure it stores attempt/error metadata and schedules exponential backoff. An expired publishing lease returns to pending. A configured maximum age/attempt policy moves the reservation to terminal failure and triggers idempotent inventory release. A bounded pending backlog stops admission for the affected session with a retryable overload response.

Inventory consumes the command with manual/record acknowledgement only after durable processing. Its MySQL transaction inserts a unique reservation row and performs batched conditional seat updates plus batched lock inserts, or atomically persists a general-admission hold/ticket range. Duplicate `reservationId` returns the prior result. Kafka redelivery after Redis succeeded but MySQL failed is safe because the reservation command is replayable and both Redis and MySQL are idempotent. MySQL outages pause/retry consumption; they do not bypass Redis or write orders directly.

After durable inventory hold, Inventory emits `InventoryHeld` via an Inventory-owned outbox in the same MySQL transaction. Order consumes it idempotently and creates a `PENDING_PAYMENT` order plus its existing order outbox. An order-side uniqueness constraint maps reservation ID to one order. The API status resource reflects queued, processing, held, pending-payment, paid, released, sold-out, or failed states.

## Payment and Release

Payment success first conditionally transitions the order from `PENDING_PAYMENT` to `PAID` and writes `PaymentSucceeded` in the order outbox. Inventory consumes it idempotently and converts all held seats/tickets to sold in one short transaction, then updates the Redis projection. Cancellation/expiry conditionally transitions the order and publishes release intent; Inventory changes only matching `HELD` reservations to available/released. A late payment after expiry is rejected or sent to refund handling and cannot resurrect a released reservation.

## Reconciliation and Compensation

Redis reservation metadata and pending/inflight ZSETs are scanned continuously. Expired publisher leases are requeued. Due pending records are republished. Reservations that exceed the publish deadline are terminalized and released through the same idempotent Lua transition. MySQL reconciliation compares Redis reservations, durable inventory holds, order states and payment states in bounded batches. Missing Redis holds for valid MySQL holds are rebuilt; missing MySQL holds with an unexpired replayable reservation are re-driven; expired reservations without a payable order are released; `PAID + HELD` is repaired to sold. Ambiguous conflicts such as `PAID + RELEASED`, duplicate ticket numbers, or unexplained counter drift are recorded for operator review rather than automatically changing money-related state.

Every repair is idempotent, records a repair reason and before/after states, and is observable through metrics: pending/inflight age and size, publish retries, consumer lag, held expiry count, reconciliation mismatches, repair outcomes and overload rejections.

## Failure Behavior

- Redis unavailable/unknown Lua result: reject queued admission; never fall through to MySQL direct writes.
- Kafka unavailable after Redis admission: reservation remains in pending ZSET and publisher retries; stop new admission at configured backlog/age thresholds.
- Publisher crashes after Kafka ack but before Redis acknowledgement: duplicate Kafka command is expected and safe.
- Inventory MySQL unavailable: do not acknowledge Kafka record; retry after recovery, while reservation TTL bounds held stock.
- Redis restart/data loss: pause queued admission, rebuild counters and reservations from durable holds and replayable records, validate conservation, then resume.
- Order unavailable: Inventory outbox retries `InventoryHeld`; do not release inventory while the reservation is still inside the payment window.
- Payment/expiry race: conditional order state transition chooses one winner; late contradictory events are quarantined for reconciliation.

## Rollout and Compatibility

Sessions default to `DIRECT`. `QUEUED` is an explicit session-level sale mode and is enabled only after Redis/Kafka readiness and schema migrations are verified. Existing APIs and clients remain compatible for direct sessions. Queued clients receive a reservation identifier and poll status. Rollout is feature-flagged and can be disabled for new admissions without invalidating in-flight reservations; workers continue draining and reconciling existing queued reservations.

## Testing

Tests cover Lua all-or-nothing multi-seat reserve, quantity capacity reserve, duplicate idempotency key, publisher failure and expired lease, acknowledged-send crash duplicate, Kafka redelivery, MySQL outage, batched six-seat persistence, payment-vs-expiry race, release idempotency, Redis rebuild, reconciliation repair/quarantine, bounded backlog rejection, and direct-mode regression. Load tests report throughput and p50/p95/p99 for same-seat, different-seat, same-session GA and mixed-session scenarios, plus MySQL write rate and zero oversell/duplicate order assertions.

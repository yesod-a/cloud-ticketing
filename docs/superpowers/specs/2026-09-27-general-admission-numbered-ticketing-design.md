# General Admission Numbered Ticketing Design

## Goal

Add a no-seat-layout ticketing mode in which a session exposes only a capacity and users request a quantity. The system atomically reserves capacity, assigns stable ticket numbers, enforces a per-session per-user purchase limit, and keeps the existing `GRID` and `ROWS` seat-selection flows unchanged.

## Compatibility and Strategy Boundary

The existing venue layout strategy registry remains the extension point, but its result becomes a layout value containing mode, capacity, and optional physical seats. Add `GENERAL_ADMISSION` as a strategy that validates capacity and returns no row/column seats. Existing `GRID` and `ROWS` continue to generate their current seat templates and accept `seatIds` in the current order API.

For a general-admission session, the public session payload includes mode, total capacity, remaining capacity, and `purchaseLimit`; it does not expose a fake seat map. The order request accepts `quantity` for this mode. A request using `seatIds` for a general-admission session, or `quantity` for a seated session, is rejected at the boundary.

The old order field remains compatible: for numbered tickets, the order stores assigned ticket numbers in a separate structured ticket table and exposes a display string through the existing order view. No existing seated order is rewritten.

## Data Model

Activity/session migration adds:

- `activity_session.layout_mode` with existing sessions defaulting to `GRID`;
- `activity_session.capacity` and `activity_session.purchase_limit`, both non-negative, with `purchase_limit = 0` meaning unlimited.

Inventory adds one `admission_inventory` row per general-admission session with capacity, reserved count, sold count, next ticket number, and a version. It does not create one inventory row for every unsold ticket. It also adds `admission_ticket` rows only when tickets are allocated, with session ID, order ID, user ID, sequential ticket number, state (`HELD`, `SOLD`, `RELEASED`), and hold expiry. Ticket numbers are monotonically assigned, unique within a session, never reused, and may have gaps after an expired, cancelled, or refunded order. Capacity is the count of active held plus sold tickets, not the largest ticket number.

Order-service adds a `user_session_purchase` aggregate keyed by `(user_id, session_id)` with `active_quantity` and `reserved_quantity`. A Flyway backfill initializes `active_quantity` from all existing `PENDING` and `PAID` orders before the new limit is enforced. New requests reserve quota by atomically increasing `reserved_quantity` under the aggregate row lock; successful order insertion moves it to `active_quantity` in the same order-database transaction. Inventory or validation failure releases the reservation. Cancellation, expiry, and refund decrement `active_quantity` only when their conditional status transition succeeds; payment leaves it unchanged. A reconciliation query/job compares aggregates to active orders and removes expired orphan reservations using the reservation TTL.

Order adds a normalized quantity and ticket-number representation while retaining `seat_ids` for seated orders. A general-admission order stores the assigned ticket numbers after reservation; payment confirmation changes ticket state from `HELD` to `SOLD` and cancellation, expiry, or refund changes it to `RELEASED`.

## Reservation Algorithm

1. Gateway/order-service authenticates the user and validates the mode-specific request. The order service resolves session mode and purchase limit from the activity service.
2. Order-service derives an idempotency hash from user, session, quantity/seat IDs, and idempotency key. It creates/locks the `(user, session)` quota row and checks for an idempotent replay after acquiring that lock. A replay returns the original order; a changed request with the same key is a conflict.
3. The order service rejects when `active_quantity + reserved_quantity + requested_quantity > purchaseLimit`; zero means unlimited. Otherwise it increases `reserved_quantity` with a short TTL and commits. Because all competing orders for one user/session serialize on the quota row, two idempotency keys cannot bypass the limit.
4. For general admission, inventory-service locks the single `admission_inventory` row and atomically checks/increments capacity. It assigns the next ticket-number range and inserts exactly `quantity` `HELD` ticket rows in that same inventory transaction.
5. Inventory-service returns the assigned ticket numbers. Order-service opens a second short transaction that conditionally moves the quota reservation to `active_quantity`, inserts a `PENDING` order with those numbers, and writes the existing outbox event. If the order transaction fails, a compensating call releases inventory and the quota reservation. A crash between services is bounded by the inventory hold and quota-reservation TTLs and repaired by reconciliation.
6. Payment confirmation calls inventory to convert the held tickets to sold. Cancellation, expiry, and refund release tickets and capacity idempotently; terminal order transitions decrement the order-side quota aggregate.

The per-user limit is owned by order-service because it owns order states and counters; global sellable capacity and ticket-number assignment are owned by inventory-service. They are not placed in a fictitious cross-database transaction. The existing synchronous reservation plus compensating release/hold expiry is retained as the saga boundary.

## Redis and Bitmap Role

For `GENERAL_ADMISSION`, the MySQL session counter is the natural compact inventory representation; a bitmap is not used to allocate monotonic non-reusable numbers. The existing Redis bitmap remains available for `GRID`/`ROWS` seat status projection, where it compresses state compared with caching full seat objects. It is an optimization only: MySQL row locks and conditional updates remain authoritative. A Redis miss, restart, or stale projection falls back to the database. Existing JMeter results showed the current bitmap-hot path was slower than DB-only for the tested read scenario, so performance claims require a new apples-to-apples benchmark and are not an acceptance assumption.

For seated sessions, the existing conditional seat updates and optional Redis seat-lock path remain unchanged. This feature does not replace the already verified DB-only or bitmap performance measurements with unverified claims.

## APIs and Frontend

Admin venue layout create/update accepts `layoutMode` and, for `GENERAL_ADMISSION`, `capacity`; `GRID`/`ROWS` keep the current row/column configuration. Session creation snapshots the venue mode and capacity. Admin session create/update accepts `purchaseLimit` (default `0`, unlimited), and the UI displays remaining capacity and the per-session purchase limit. For compatibility and historical integrity, session mode and capacity become immutable after publishing or after the first order; an unpublished draft can be edited.

The user booking view branches by mode: seated sessions render the existing seat picker; general-admission sessions render a quantity stepper bounded by remaining capacity and the user's remaining allowance. The order client sends either `{ seatIds, ... }` or `{ quantity, ... }`.

Order details display assigned ticket numbers for general admission and seat labels for seated orders. Existing payment, cancellation, expiry, refund, outbox, and admin-order flows remain the same at the lifecycle boundary.

## Failure Handling

- Capacity or per-user limit failure returns HTTP 409 with a stable error code.
- Duplicate ticket-number insertion rolls back the reservation; repeated failure is surfaced as a temporary inventory error rather than silently changing the allocation.
- Order insert failure releases held tickets exactly once.
- Expiry and cancellation use conditional state transitions so repeated scanners or callbacks cannot release capacity twice.
- A pending reservation that outlives an order-service crash expires in inventory and is reconciled without consuming capacity permanently.

## Testing and Acceptance

Backend tests cover strategy registration, mode validation, capacity/limit migrations and quota backfill, concurrent oversell attempts, concurrent same-user limit attempts, idempotent replay, ticket-number uniqueness and monotonicity, payment confirmation, release paths, Redis outage fallback for seated modes, expired reservation recovery, and quota reconciliation. Contract tests cover old seated requests and new quantity requests. Frontend tests cover both booking branches, quantity bounds, limit errors, remaining-capacity display, and ticket-number display. JMeter scenarios record throughput, conflict rate, zero oversell, and zero limit violations for the DB-authoritative general-admission path; bitmap is benchmarked only for the seated path where it is applicable.

## Non-goals

- Replacing existing seated inventory with a Redis-only source of truth.
- General-admission seat coordinates, rows, or venue seat templates.
- Cross-session purchase limits or account-wide limits.
- Ticket-number reuse; released allocations retain their historical number and subsequent orders receive later numbers.

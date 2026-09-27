# Design-Pattern Refactor and MyBatis-Plus Migration

## Scope

Applied the refactor proposed in `设计模式.md` and replaced the JDBC persistence layer of all four
stateful services with MyBatis-Plus. Flyway remains the schema owner; no migration was added or
changed, so an existing database upgrades by simply restarting the new build.

## Pattern mapping

| Pattern | Where it landed | What it replaced |
|---|---|---|
| AOP + annotations | `common-security`: `@RequirePermission`, `@RequireScope`, `@RequireInternalToken`, `@AuditAction` + `AuthorizationAspect` | 40+ hand-written permission/scope/audit/token checks across 9 controllers |
| Strategy + factory | `activity-service` `layout/`: `GridSeatLayoutStrategy`, `RowSeatLayoutStrategy`, `SeatLayoutStrategyRegistry` | an `if/else` chain inside one long `computeVenueLayout` method |
| Strategy + factory | `order-service` `payment/`: `PaymentChannel` with WeChat/Alipay/UnionPay + `PaymentChannelRegistry` | a hard-coded `Set.of("WECHAT","ALIPAY","UNIONPAY")` with a default branch |
| Template method | `order-service` `event/OutboxEventWriter` | 3 hand-written outbox INSERTs and 4 copies of `jsonEscape`; payloads are now typed records serialised by Jackson |
| Decorator | `order-service` `client/InternalServiceClient` | bare `RestClient` calls with no timeout, retry or trace propagation |
| Command objects | `*.api.command` records in activity, inventory, auth and order | `Map<String,String>` / `Map<String,Object>` request bodies |
| Layering | `ActivityCatalog` (93 giant lines) split into `ActivityService` / `VenueService` / `SessionService` over five repositories | one class mixing SQL, validation, audit and HTTP hand-off |
| Shared read model | `common-web` `PageResult`, `ApiResponse`; `common-security` `ResourceScopeRule` | three copies of the scope matcher, four `page(...)` helpers, six response envelopes |

Deliberately **not** changed, matching the reference document: seat and order state transitions stay
conditional `UPDATE ... WHERE status = ...` statements. Wrapping them in a state pattern would split
one atomic statement into read-decide-write and reintroduce a race.

## Persistence migration

- `common-mybatis` registers `MybatisPlusInterceptor` (pagination + block-attack guard) through
  `AutoConfiguration.imports`; every service gets paging without repeating configuration.
- Each service now has `persistence/entity` (table mapping), `persistence/mapper` (`BaseMapper` plus
  annotated statements) and repositories that return domain types.
- Listing endpoints page in the database instead of loading every row and slicing in memory; scoped
  listings push the `FIND_IN_SET` scope predicate into SQL so page totals match what the caller may
  read.
- `auth-service` dropped Spring Data JDBC and its `UuidConverters`; a `UUID` type handler maps to the
  existing `BINARY(16)` columns, so the schema is untouched.
- Manual `LIMIT/OFFSET` + `COUNT(*)` pairs, row-mapper boilerplate, and the duplicated `normalizeSeats`
  (now `common-domain` `SeatIds`) are gone.

## Verification

- `mvn clean install`: 11 modules SUCCESS, 161 tests, 0 failures (was 82 tests before the refactor).
- New tests cover the aspect through the real auto-configuration (`ApplicationContextRunner` plus
  `AopAutoConfiguration`), the layout and payment strategies, and every repository against mocked
  mappers.
- Live `docker compose` stack (MySQL/Redis/Kafka/Nacos + 4 services + gateway), against the **existing
  database volume** so the unchanged schema and seeded data were exercised:
  - `tests/auth/AuthFlow.ps1` PASS, `tests/security/TokenReplay.ps1` PASS,
    `tests/auth/AuthorizationMatrix.ps1` PASS (anonymous 401, ordinary user 403),
    `tests/e2e/MultiActivityOrderFlow.ps1` PASS,
    `tests/load/SeatLockLoadTest.ps1` PASS (100 concurrent attempts on one seat → 1 success, 99 × 409).
  - Payment flow: order 19900 → intent `ALIPAY` with QR → pay `SUCCESS`, order `PAID`, seat `SOLD`,
    lock `CONFIRMED`, and `providerTransactionId` shaped by the resolved channel (`ALI-…`).
  - Outbox rows for `OrderCreated` / `PaymentSucceeded` / `OrderRefunded` were written with
    `trace_id` populated from the request and published to Kafka; the payloads are valid JSON with
    `amountMinor` as a number.
  - Refund flow: request → approve → order `REFUNDED`, `OrderRefunded` emitted; re-submitting the same
    refund returns the existing request (409-free).
  - Cancellation: `PENDING` order cancelled and the seat returned to `AVAILABLE`.
  - Scoped operator (OPERATOR + one `ACTIVITY` scope): `/api/admin/activities` returned exactly that
    activity, `/api/admin/activities/{other}/sessions` returned 0 rows, and with INVENTORY_ADMIN the
    scoped inventory listing returned 120 of the 140 seats.
  - Scoped order/refund review: with a `SESSION` scope bound, `/api/orders/admin` returned 2 of 29
    orders and `/api/orders/admin/refunds` 1 of 2; approving a refund outside that session scope was
    refused while the same refund was approved by an operator holding `system:config`.
  - `/api/admin/activities/{id}/seats` paged 46 seats across 16 pages, and `GET
    /api/internal/sessions/{id}` answered only when the internal token header was present.
  - Auditing through the aspect: `VENUE_LAYOUT_GENERATED` recorded `6 seats`, `ACTIVITY_UPDATED` /
    `ACTIVITY_PUBLISHED` / `LAYOUT_FROZEN` recorded before and after snapshots,
    `SEAT_STATUS_ADJUSTED` recorded `AVAILABLE → DISABLED` with the operator reason, and a repeated
    role grant wrote no audit line because nothing changed.
  - The public read cache still works (`X-Cache: MISS` then `HIT`) and boundaried writes invalidate it.

## Known behaviour changes

- Audit payloads are now produced by Jackson from typed records: `amountMinor` is a JSON number in
  every order event (previously a number only in `PaymentSucceeded`), and the internal
  `requestHash` is no longer copied into `OrderCreated`.
- A direct (non-gateway) call to `GET /api/auth/me` without the internal service token is refused by
  the shared aspect as 403 instead of the previous 401. Gateway traffic always carries the token, and
  the gateway is what answers 401 to unauthenticated browsers, so the documented external behaviour is
  unchanged.
- Additive response fields: role grant returns `changed`, scope bind/unbind return `changed` and
  create returns `changed`. Existing clients read `data`/named fields and ignore the extra key.
- `GET /api/admin/venues/{id}/layout` with an unknown mode is still an unhandled
  `IllegalArgumentException` (HTTP 500) as before; the message now lists the registered modes.

## Pre-existing defect found

`tests/e2e/MultiActivityOrderFlow.ps1` and `tests/load/SeatLockLoadTest.ps1` still read the public
activity list as a bare array (`data[0]`). Commit `1c99024` changed `data` into a page object
(`data.items`) and did not update these two scripts, so both were failing before this work. They now
read `data.items` and pass; no production behaviour was involved.

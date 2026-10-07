# General Admission Numbered Ticketing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add quantity-based general-admission sessions with automatic non-reusable ticket numbers and per-session per-user limits while preserving GRID/ROWS seat ordering.

**Architecture:** `GENERAL_ADMISSION` is a new layout strategy and session mode. Inventory owns a compact session counter and allocated ticket rows; order-service owns the per-user quota aggregate and order lifecycle. The cross-service reservation is a compensating saga with TTLs, not a fake distributed transaction. Redis remains an optional seated-seat projection only.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, Flyway, MySQL row locks/unique constraints, Redis optional projection, Vue 3/Vite, JMeter.

**Spec:** `docs/superpowers/specs/2026-09-27-general-admission-numbered-ticketing-design.md`

## Global Constraints

- Existing GRID/ROWS requests and order payloads remain valid.
- `purchaseLimit = 0` means unlimited; active count includes PENDING and PAID only.
- Ticket numbers are sequential per session, monotonic, unique, and never reused.
- MySQL remains the final inventory source of truth; Redis failures fall back safely.

### Task 1: Session mode and strategy

**Files:**
- Create: `services/activity-service/src/main/resources/db/migration/V11__general_admission_session.sql`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/domain/Session.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/entity/SessionEntity.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/SessionRepository.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/layout/GeneralAdmissionLayoutStrategy.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/domain/SeatLayoutRules.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/command/SessionCommands.java`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/SeatLayoutStrategyTest.java`

- [ ] Add failing tests for GENERAL_ADMISSION capacity validation and session defaults.
- [ ] Run focused tests and confirm failure.
- [ ] Add mode/capacity/purchase-limit fields, migration defaults, strategy registration, and typed session mapping.
- [ ] Run activity-service tests.
- [ ] Commit with `feat: add general admission session mode`.

### Task 2: Admission inventory and ticket allocation

**Files:**
- Create: `services/inventory-service/src/main/resources/db/migration/V9__admission_inventory.sql`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/entity/AdmissionInventoryEntity.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/entity/AdmissionTicketEntity.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/mapper/AdmissionInventoryMapper.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/persistence/mapper/AdmissionTicketMapper.java`
- Create: `services/inventory-service/src/main/java/com/cloudticket/inventory/service/AdmissionReservationService.java`
- Modify: `services/inventory-service/src/main/java/com/cloudticket/inventory/api/InternalInventoryController.java`
- Test: `services/inventory-service/src/test/java/com/cloudticket/inventory/AdmissionReservationServiceTest.java`

**Interfaces:** `reserveQuantity(orderId, userId, sessionId, quantity, ttlSeconds) -> AdmissionReservation`; `confirm(orderId)`; `release(orderId)`; `remaining(sessionId)`. All mutations use row locks and conditional state transitions.

- [ ] Add failing tests for capacity oversell, sequential ticket assignment, repeated release/confirm, and ticket number uniqueness.
- [ ] Run focused tests and confirm failure.
- [ ] Implement schema, row-lock repositories, allocator, controller request fields, and stable 409 errors.
- [ ] Run inventory-service tests.
- [ ] Commit with `feat: add general admission inventory`.

### Task 3: Order quota and quantity flow

**Files:**
- Create: `services/order-service/src/main/resources/db/migration/V11__session_purchase_quota.sql`
- Create: `services/order-service/src/main/java/com/cloudticket/order/persistence/entity/UserSessionPurchaseEntity.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/persistence/mapper/UserSessionPurchaseMapper.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/persistence/entity/TicketOrderEntity.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/persistence/OrderRepository.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/api/OrderController.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/client/InventoryReservationClient.java`
- Test: `services/order-service/src/test/java/com/cloudticket/order/OrderRepositoryTest.java`

- [ ] Add failing tests for quantity validation, same-user concurrent quota reservation, idempotent replay, and release on cancellation/expiry/refund.
- [ ] Run focused tests and confirm failure.
- [ ] Implement quota reservation with TTL, quantity-aware order hash, inventory calls, ticket persistence, and lifecycle compensation.
- [ ] Run order-service tests.
- [ ] Commit with `feat: enforce general admission purchase limits`.

### Task 4: Public/admin APIs and web booking branch

**Files:**
- Modify: activity session controllers/DTOs and gateway routes.
- Modify: `web/src/types.ts`, `web/src/api.ts`, `web/src/views/BookingView.vue`.
- Modify: `web/src/adminApi.ts`, `web/src/views/admin/AdminLayout.vue`, and `web/src/views/admin/VenueInventoryView.vue`.
- Test: existing booking/admin Vitest suites plus new mode-specific tests.

- [ ] Add failing contract/UI tests for quantity requests, limit bounds, remaining capacity, and ticket-number display.
- [ ] Run focused tests and confirm failure.
- [ ] Implement mode-specific payload validation and UI branch without changing seated UI behavior.
- [ ] Run `npm test` and `npm run build`.
- [ ] Commit with `feat: add general admission booking UI`.

### Task 5: Expiry, reconciliation, and performance verification

- [ ] Add scheduled cleanup/reconciliation for expired admission holds and quota reservations.
- [ ] Add tests for crash-window compensation and repair of aggregate counters.
- [ ] Run JMeter scenarios for DB-authoritative quantity flow and existing seated bitmap flow, recording throughput, conflict rate, zero oversell, and zero limit violations separately.
- [ ] Run all Maven and web tests/build.
- [ ] Commit with `test: verify general admission concurrency`.

# Coupon and Comment System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or superpowers:subagent-driven-development) to implement this plan task-by-task.

**Goal:** Add ticket-aware coupons and activity comments to Cloud Ticketing while preserving existing order, inventory, JWT and gateway behavior.

**Architecture:** Add `promotion-service` and `comment-service` with their own Flyway schemas and MyBatis repositories. Promotion owns coupon lifecycle and discount strategies; order-service asks it for an idempotent quote and stores the resulting monetary snapshot. Comment-service owns visible/hidden comments and Redis-backed like de-duplication, and calls an internal order endpoint for paid-activity eligibility. Gateway and event constants expose the new boundaries, while Vue adds coupon selection, comment pagination, and a personal coupon view.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis-Plus, Flyway, Redis/Lua, Kafka, Spring Cloud Gateway, Vue 3, TypeScript, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-07-coupon-comment-design.md`

## Global Constraints

- Preserve existing uncommitted activity, inventory, order, profile, and frontend changes.
- `QUEUED` orders and coupon stacking remain unsupported in the first version.
- Money is integer minor units; the server is the only source of discount calculation.
- Internal service calls require `X-Internal-Service-Token`; browser clients never call internal endpoints.
- Every lifecycle command is idempotent by reservation/event id.
- Existing tests and stable `data-testid` values must continue to pass.

### Task 1: Scaffold promotion-service and coupon schema

**Files:**
- Create: `services/promotion-service/pom.xml`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/PromotionApplication.java`
- Create: `services/promotion-service/src/main/resources/application.yml`
- Create: `services/promotion-service/src/main/resources/db/migration/V1__promotion_schema.sql`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/persistence/entity/CouponEntity.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/persistence/entity/CouponScopeEntity.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/persistence/entity/UserCouponEntity.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/persistence/entity/CouponReservationEntity.java`
- Create: matching `mapper/*.java` interfaces extending `BaseMapper`
- Test: `services/promotion-service/src/test/java/com/cloudticket/promotion/PromotionSchemaTest.java`

**Interfaces:** Entities expose UUID ids, integer minor-unit amounts, enum-like status strings, timestamps, and the unique order/user-coupon reservation keys described in the spec. Mappers provide lookup by user/status, scope matching, and reservation-by-order operations.

- [ ] Add the Maven module to the root `<modules>` list and copy only existing common dependencies plus Redis, Kafka, Flyway, MySQL and test starters.
- [ ] Create the four tables and indexes, including `UNIQUE(order_id)` on reservations and `UNIQUE(coupon_id,user_id)` on user coupons.
- [ ] Implement MyBatis entities/mappers using the repository's existing UUID and `@TableName` conventions.
- [ ] Test that Flyway SQL contains all required statuses, scope uniqueness, and reservation uniqueness.
- [ ] Run `mvn -q -pl services/promotion-service -am test -DskipTests` and fix compilation before continuing.

### Task 2: Implement discount strategies and user coupon lifecycle

**Files:**
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/domain/Coupon.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/domain/DiscountStrategy.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/domain/PriceDiscount.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/domain/RateDiscount.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/domain/NoThresholdDiscount.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/service/CouponService.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/api/PromotionController.java`
- Create: `services/promotion-service/src/test/java/com/cloudticket/promotion/DiscountStrategyTest.java`
- Create: `services/promotion-service/src/test/java/com/cloudticket/promotion/CouponServiceTest.java`

**Interfaces:** `DiscountStrategy#canUse(int,Coupon)`, `calculateDiscount(int,Coupon)`, and `describe(Coupon)`; `CouponService#available(userId,activityId,sessionId)`, `claim(userId,couponId)`, and `myCoupons(userId,status,page,size)`.

- [ ] Write strategy tests for threshold failure, percentage rounding/cap, and discount never exceeding original price.
- [ ] Implement a strategy registry keyed by `discount_type` and validate non-negative values at the domain boundary.
- [ ] Implement available-coupon filtering for published status, issue/term windows, and ALL/ACTIVITY/SESSION scopes.
- [ ] Implement claim validation and duplicate-safe user coupon insertion.
- [ ] Add authenticated endpoints under `/api/promotions` and return `PageResult` for the user list.
- [ ] Run the focused promotion tests and verify the JSON contract with MockMvc.

### Task 3: Add atomic claim, quote, and reservation state machine

**Files:**
- Create: `services/promotion-service/src/main/resources/redis/claim-coupon.lua`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/redis/CouponClaimService.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/service/CouponReservationService.java`
- Create: `services/promotion-service/src/main/java/com/cloudticket/promotion/api/InternalPromotionController.java`
- Create: `services/promotion-service/src/test/java/com/cloudticket/promotion/CouponClaimServiceTest.java`
- Create: `services/promotion-service/src/test/java/com/cloudticket/promotion/CouponReservationServiceTest.java`

**Interfaces:** `POST /api/internal/promotions/quote` accepts `{orderId,userId,activityId,sessionId,originalAmountMinor,couponId}` and returns `{reservationId,couponId,discountAmountMinor,payableAmountMinor}`. Lifecycle endpoints consume, release, and restore by reservation id.

- [ ] Write tests for Lua result codes (offline, sold out, duplicate, user limit, success) and reservation transitions `HELD -> CONSUMED/RELEASED/RESTORED/EXPIRED`.
- [ ] Store coupon inventory and per-user counters in Redis; execute the Lua script through `StringRedisTemplate` with one atomic call.
- [ ] Make quote idempotent by `orderId`, reusing an existing reservation and rejecting QUEUED callers.
- [ ] Add conditional SQL updates so repeated lifecycle requests are successful no-ops.
- [ ] Add a scheduled reconciliation query for stale HELD reservations and explicit failure logging.
- [ ] Run all promotion-service tests with Redis-independent mocked script execution.

### Task 4: Wire order-service amount snapshots and coupon events

**Files:**
- Modify: `services/order-service/src/main/resources/db/migration/V13__order_coupon_amount_snapshot.sql`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/client/ActivitySessionClient.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/client/PromotionClient.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/persistence/OrderRepository.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/api/OrderController.java`
- Modify: `services/order-service/src/main/java/com/cloudticket/order/PaymentService.java`
- Create: `services/order-service/src/main/java/com/cloudticket/order/api/InternalOrderEligibilityController.java`
- Modify: `common/common-events/src/main/java/com/cloudticket/common/events/EventTypes.java`
- Test: `services/order-service/src/test/java/com/cloudticket/order/OrderCouponSnapshotTest.java`

**Interfaces:** Extend direct-order request with optional `couponId`; persist `activity_id`, `original_amount_minor`, `discount_amount_minor`, `amount_minor`, `coupon_id`, and `coupon_reservation_id`. Add internal `GET /api/internal/orders/users/{userId}/activities/{activityId}/paid` returning `{eligible,count}`.

- [ ] Add the migration with defaults that keep old rows readable and update entity/mapper projections.
- [ ] Ensure the internal session response includes `activityId`, and expose it from `SessionInfo`.
- [ ] Generate the order id before calling promotion quote; pass the idempotency key and release the reservation when inventory or insert fails.
- [ ] Make payment, cancel, expiry, and refund outbox payloads include reservation id and add coupon lifecycle event constants.
- [ ] Implement paid-activity qualification with a narrow count query that exposes no order details.
- [ ] Test no-coupon, coupon, repeated idempotency, and inventory-failure compensation paths.

### Task 5: Scaffold comment-service and comment APIs

**Files:**
- Create: `services/comment-service/pom.xml`
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/CommentApplication.java`
- Create: `services/comment-service/src/main/resources/application.yml`
- Create: `services/comment-service/src/main/resources/db/migration/V1__comment_schema.sql`
- Create: entities/mappers under `services/comment-service/src/main/java/com/cloudticket/comment/persistence/`
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/client/OrderEligibilityClient.java`
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/service/CommentService.java`
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/api/CommentController.java`
- Create: `services/comment-service/src/test/java/com/cloudticket/comment/CommentServiceTest.java`

**Interfaces:** Public endpoints follow `/api/activities/{activityId}/comments`, `/api/comments/{id}/replies`, and the like endpoints in the spec. `CommentService#create` accepts only nonblank content <= 1000 characters and a paid eligibility result.

- [ ] Add schema for comments and composite-key like records with activity/status and parent indexes.
- [ ] Implement one-level parent normalization, reply count maintenance, visible-only public paging, and authenticated creation.
- [ ] Call order-service's internal eligibility endpoint with the shared token before inserting a comment.
- [ ] Add moderation endpoints guarded by `comment:moderate` and preserve hidden content for administrators.
- [ ] Test qualification denial, parent normalization, hidden filtering, and page boundaries.

### Task 6: Add Redis like de-duplication and asynchronous count persistence

**Files:**
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/redis/CommentLikeService.java`
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/event/CommentLikeCountPublisher.java`
- Create: `services/comment-service/src/main/java/com/cloudticket/comment/event/CommentLikeCountConsumer.java`
- Modify: `common/common-events/src/main/java/com/cloudticket/common/events/EventTypes.java`
- Test: `services/comment-service/src/test/java/com/cloudticket/comment/CommentLikeServiceTest.java`

**Interfaces:** Like/unlike returns `{liked,likeCount}`; Redis sets use `comment:likes:{commentId}:{userId}`-equivalent membership keys and a ZSet for dirty counts. Kafka payload contains event id, comment id, delta, and observed count.

- [ ] Test duplicate like/unlike calls and database fallback behavior.
- [ ] Use Redis Set membership for fast idempotent toggles and write the durable record with a unique composite key.
- [ ] Batch dirty counts into `CommentLikeCountChanged` events and conditionally update database counts.
- [ ] Add reconciliation that compares Redis dirty state and persisted count without claiming strong real-time consistency.

### Task 7: Gateway, permissions, frontend integration, and verification

**Files:**
- Modify: `services/gateway-service/src/main/java/com/cloudticket/gateway/config/RouteConfiguration.java`
- Modify: `services/auth-service/src/main/resources/db/migration/V5__promotion_comment_permissions.sql`
- Modify: `web/src/api.ts`, `web/src/adminApi.ts`, `web/src/types.ts`
- Modify: `web/src/views/BookingView.vue`, `web/src/views/PaymentView.vue`, `web/src/views/OrdersView.vue`, `web/src/views/ProfileView.vue`, `web/src/views/admin/AdminLayout.vue`
- Create: focused Vue tests under `web/src/__tests__/coupon-comment.test.ts`

**Interfaces:** Gateway routes `/api/promotions/**`, `/api/activities/*/comments`, `/api/comments/**`, and `/api/admin/{promotions,comments}/**`. Direct BookingView requests send optional `couponId`; queued mode renders no coupon selector. Comments expose stable `data-testid` values for list, input, submit, reply, and like controls.

- [ ] Add service routes and seed the six permissions while retaining existing role grants.
- [ ] Add typed API helpers and render coupon pricing from server snapshots only.
- [ ] Add comment loading, pagination, reply, and optimistic like rollback; show the editor only when the paid-activity flag is true.
- [ ] Add the personal coupon list and minimal admin coupon/moderation panels without changing existing navigation test ids.
- [ ] Run `mvn --% -q -Dsurefire.failIfNoSpecifiedTests=false test`, `npm run test -- --run`, and `npm run build`.
- [ ] Record remaining integration prerequisites (Redis/Kafka/MySQL) separately from verified unit behavior.

## Self-review checklist

- Spec coverage: coupon definition/claim/quote/state machine, order snapshot/event flow, comments/replies/moderation, likes, gateway, permissions, frontend, and tests each have a task.
- No placeholder task wording is used; each task names concrete paths, APIs, and verification commands.
- Later tasks consume only interfaces defined in earlier tasks (`PromotionClient`, eligibility endpoint, event names, and typed frontend helpers).

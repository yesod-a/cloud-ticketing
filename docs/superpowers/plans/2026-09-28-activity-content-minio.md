# Activity Content and MinIO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add activity descriptions and MinIO-backed cover/detail images to the admin and public activity flows.

**Architecture:** activity-service owns activity metadata and image metadata. A small storage adapter wraps the MinIO Java client; the bucket stays private and public responses contain short-lived presigned GET URLs. Uploads are compensated when metadata persistence fails, and image mutations invalidate the existing public-read cache.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, Flyway, MinIO Java SDK, Vue 3/Vite, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-27-activity-content-minio-design.md`

## Global Constraints

- Only JPEG, PNG, and WebP are accepted, with a 5 MiB per-file limit.
- Each activity has at most one active cover and nine active detail images.
- MinIO credentials and raw object keys never appear in browser responses.
- Existing permissions, scopes, audit actions, cache invalidation, and seated booking behavior remain compatible.

### Task 1: Activity content persistence

**Files:**
- Create: `services/activity-service/src/main/resources/db/migration/V9__activity_content.sql`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/entity/ActivityEntity.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/domain/Activity.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/ActivityRepository.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityService.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/command/ActivityCommands.java`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityRepositoryTest.java`

**Interfaces:** Add `description` to `Activity`; create/update methods accept it and trim null to empty. Existing callers using two arguments remain source-compatible through overloads.

- [ ] Write a failing repository test asserting create/update preserves description and old overloads use empty description.
- [ ] Run `mvn -pl services/activity-service -Dtest=ActivityRepositoryTest test` and confirm failure because the field and migration are absent.
- [ ] Add the Flyway column, entity field, record field, command fields, repository mapping, and service overloads.
- [ ] Run the focused test and then existing activity-service tests.
- [ ] Commit with `feat: add activity descriptions`.

### Task 2: Image metadata and storage adapter

**Files:**
- Modify: `services/activity-service/pom.xml`
- Modify: `services/activity-service/src/main/resources/application.yml`
- Create: `services/activity-service/src/main/resources/db/migration/V10__activity_image.sql`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/entity/ActivityImageEntity.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/mapper/ActivityImageMapper.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/persistence/ActivityImageRepository.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/image/ActivityImageStorage.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/image/MinioActivityImageStorage.java`
- Create: `services/activity-service/src/main/java/com/cloudticket/activity/image/ActivityImageService.java`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityImageServiceTest.java`

**Interfaces:** `ActivityImageStorage.put(String key, String contentType, InputStream body, long size)`, `delete(String key)`, and `presignedGet(String key)`. `ActivityImageService` exposes upload, delete, cover, reorder, and public image view methods.

- [ ] Write tests for invalid MIME/size, generated UUID keys, one-cover replacement, nine-detail limit, reorder validation, and compensation delete after metadata failure.
- [ ] Run the focused test and confirm missing service/adapter failures.
- [ ] Add MinIO dependency/configuration, migration, metadata mapper/repository, adapter, validator, and transactional service.
- [ ] Run focused tests and verify existing MyBatis metadata tests still pass.
- [ ] Commit with `feat: add minio activity image storage`.

### Task 3: Activity admin/public APIs

**Files:**
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityAdminController.java`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityController.java`
- Create or modify: `services/activity-service/src/main/java/com/cloudticket/activity/api/ActivityImageViews.java`
- Modify: `services/gateway-service/src/main/java/com/cloudticket/gateway/config/RouteConfiguration.java`
- Modify: `docker-compose.yml`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/ActivityAdminControllerTest.java`

**Interfaces:** Multipart image endpoints use `file` and `imageType`; public activity views include `description`, `coverImageUrl`, and ordered `images`. Admin mutations require `activity:write` and activity scope and emit audit actions.

- [ ] Add controller tests for description payloads, multipart upload, delete, cover, order, and public DTOs.
- [ ] Run them and confirm endpoint failures.
- [ ] Implement endpoints, response mapping, cache invalidation, MinIO Compose service, bucket env vars, and gateway route coverage.
- [ ] Run activity-service controller tests.
- [ ] Commit with `feat: expose activity image APIs`.

### Task 4: Admin and public web UI

**Files:**
- Modify: `web/src/types.ts`
- Modify: `web/src/api.ts`
- Modify: `web/src/adminApi.ts`
- Modify: `web/src/App.vue`
- Modify: `web/src/views/BookingView.vue`
- Modify: `web/src/views/admin/AdminLayout.vue`
- Modify: `web/src/styles.css`
- Test: `web/src/__tests__/activity-list.test.ts`
- Test: `web/src/__tests__/booking-api.test.ts`

- [ ] Add failing API/component tests for optional description/image fields, multipart helpers, placeholder cover, gallery, and admin editor controls.
- [ ] Run focused Vitest tests and confirm missing field/control failures.
- [ ] Implement typed helpers and UI with upload/delete/reorder feedback while preserving existing booking flow.
- [ ] Run `npm test` and `npm run build`.
- [ ] Commit with `feat: add activity content management UI`.

### Task 5: Integration verification

- [ ] Start Compose with MinIO and run Flyway-backed activity-service tests.
- [ ] Exercise upload, public detail, replacement, deletion, and cache invalidation with HTTP requests.
- [ ] Run `mvn test` and `npm test`/`npm run build`.
- [ ] Record any environment limitation without claiming unverified success.

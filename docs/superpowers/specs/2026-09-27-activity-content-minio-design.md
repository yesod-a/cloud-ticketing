# Activity Content and MinIO Image Design

## Goal

Extend activities with a description and managed images while keeping the existing activity-service ownership, public catalogue endpoints, admin permissions, audit trail, and two-level public-read cache. Images are stored in MinIO; the browser never receives MinIO credentials.

## Scope

- Add an optional activity description.
- Support one cover image and up to nine detail images per activity.
- Allow administrators to upload, replace, delete, and reorder images.
- Show the cover in public activity listings and description plus ordered images in activity details.
- Preserve existing activity, session, seat, order, and authorization behavior.

## Storage and Data Model

Flyway adds `description TEXT NOT NULL DEFAULT ''` to `activity` and creates `activity_image` with:

- `id`, `activity_id`, `object_key`, `original_name`, `content_type`, `size_bytes`;
- `image_type` (`COVER` or `DETAIL`), `sort_order`;
- `status` (`ACTIVE`, `DELETE_PENDING`, `DELETED`);
- `created_at`, `updated_at`.

The database owns image metadata and ordering. A transaction locks the activity row before counting or changing images, so concurrent uploads cannot create two active covers or more than nine active detail images. A cover and detail image are separate records; the same record cannot have both roles. Activity deletion marks image rows `DELETE_PENDING`; object cleanup is retried independently so MinIO downtime does not strand the activity transaction.

## MinIO Adapter

The activity service receives `MultipartFile`, validates the actual content type and size, and writes to a private bucket. Only JPEG, PNG, and WebP are accepted; each file is limited to 5 MiB. Object keys use `activities/{activityId}/{uuid}.{extension}` and never use a client-supplied path or filename as a key.

The storage adapter exposes upload, delete, and short-lived presigned GET URL operations. Upload flow is: validate, upload object, persist metadata, and compensate by deleting the object if persistence fails. Cleanup failures are logged and remain `DELETE_PENDING` for retry. A configurable endpoint, bucket, access key, secret key, URL expiry, and upload limits are supplied through activity-service configuration and Compose environment variables.

## APIs

Existing activity create/update requests gain `description`:

```text
POST /api/admin/activities
PUT  /api/admin/activities/{id}
```

Image administration:

```text
POST   /api/admin/activities/{id}/images       multipart file + imageType
DELETE /api/admin/activities/{id}/images/{imageId}
PUT    /api/admin/activities/{id}/images/{imageId}/cover
PUT    /api/admin/activities/{id}/images/order body { imageIds: [...] }
```

All image mutations require the existing `activity:write` permission and activity scope, emit audit actions, and invalidate the public-read cache generation. Uploading a cover replaces the previous cover atomically. Detail uploads append at the next order position. Reordering must contain exactly the active detail IDs for that activity; cover order is not reorderable.

Public `GET /api/activities` returns the description and one `coverImageUrl`. Public `GET /api/activities/{id}` returns description, cover URL, and ordered detail image objects containing image ID, URL, and sort order. URLs are short-lived presigned GET URLs and are regenerated when the response is built. Missing covers remain valid and render with the existing frontend placeholder.

## Frontend

The admin activity editor gains a description textarea and image management controls for cover upload, detail multi-upload, preview, reorder, replacement, and deletion. Upload progress and validation errors are shown without closing the editor.

The public list renders a cover or placeholder. The booking/activity detail view renders the description and an ordered image gallery while preserving the existing session and seat-selection flow. Typed API models remain backward-compatible by making new fields optional when consuming older responses.

## Failure and Security Rules

- Reject empty files, unsupported MIME types, files larger than 5 MiB, and malformed image content with 400.
- Never expose MinIO credentials or raw object keys to the browser.
- Enforce activity scope before reading or mutating image metadata.
- If an old object cannot be deleted during replacement, keep the new database record authoritative and queue the old key for cleanup.
- Do not make public image URLs permanent; a cache miss can safely obtain a fresh URL.

## Testing and Acceptance

Backend tests cover description mapping, MIME/size/content validation, generated keys, cover replacement, concurrent detail-count limits, reorder validation, permission/scope checks, compensation after database failure, cleanup retry state, public DTOs, and cache invalidation. Frontend tests cover editor upload/delete/reorder, list placeholder/cover rendering, detail description/gallery rendering, and failed-upload feedback. Existing activity, session, authorization, and cache tests remain green.

## Non-goals

- Direct browser-to-MinIO uploads or multipart resumable uploads.
- Image editing, cropping, moderation, CDN provisioning, or permanent public buckets.
- More than one cover or more than nine detail images per activity.

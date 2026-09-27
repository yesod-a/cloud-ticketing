# Registration and Profile Center Design

## Goal

Complete the customer account flow with registration, an authenticated profile center, avatar upload, profile editing, password change, and logout while preserving the existing gateway-issued JWT boundary and order navigation.

## Scope

- Keep the existing phone-or-email registration flow and immediately sign the new account in.
- Add an authenticated profile center reachable from the main navigation.
- Display account ID, phone/email, status, creation date, nickname, and avatar.
- Allow nickname updates and avatar upload.
- Allow password changes using the current password and the existing password policy.
- Keep phone and email read-only in this release; verification and rebinding are out of scope.
- Store avatar binaries in a configurable auth-service local directory. Store only the generated filename in MySQL. Return a stable public avatar URL so the browser can render it without attaching an Authorization header.

## Backend Architecture

The auth service remains the owner of user identity and profile data. A Flyway migration adds `avatar_filename` to `auth_user`. `AuthUserMapper` gains explicit select/update statements for profile fields so password hashes and security counters are not accidentally exposed or overwritten.

The gateway continues routing `/api/auth/**` to auth-service and injecting `X-Internal-Service-Token`. The existing JWT filter supplies the trusted `X-User-Id` header for protected profile operations. A public avatar GET is allowed by the anonymous path policy because avatars are presentation data; the response is constrained to the generated filename and image content type.

The auth API adds:

- `GET /api/auth/me`: return the current `UserView`, including `avatarUrl` and `createdAt`.
- `PATCH /api/auth/me`: accept `{ nickname }`, trim it, enforce a maximum of 120 characters, and return the updated view.
- `POST /api/auth/me/avatar` with multipart field `file`: accept JPEG, PNG, or WebP up to 2 MiB, replace the prior file only after the new file is safely written, update the database, and return the updated view.
- `POST /api/auth/me/password` with `{ currentPassword, newPassword }`: verify the current hash, apply `PasswordPolicy`, update the hash, increment `scope_version`, and return no token. The client clears its session and returns to login so all existing sessions must re-authenticate.
- `GET /api/auth/avatars/{userId}`: stream the stored image or return 404 when none exists.

Failures use existing `ApiResponse`/exception handling conventions: 400 for malformed profile or image input, 401 for missing/invalid credentials, 404 for missing avatar, and 409 only for the existing registration duplicate case. Uploaded files use generated UUID names and never use a client-provided path.

## Frontend Architecture

`App.vue` owns a `profile` view state alongside the existing orders/admin/booking states. A new `ProfileView.vue` loads the authenticated profile on entry, displays an avatar with initials fallback, and contains separate forms for nickname, avatar selection, and password change. The navigation receives a `个人中心` action for signed-in users.

`authApi.ts` adds typed helpers for profile loading, nickname update, avatar upload, and password change. Avatar upload uses `FormData` and the shared `api` helper so credentials and refresh behavior remain consistent. After a successful password change the shared session is cleared and the app returns to login; after profile updates the view refreshes its local profile object.

The UI follows existing dark, compact styling, uses a stable circular avatar frame, exposes upload errors in an alert, disables submit controls while requests are active, and works at the existing mobile breakpoint.

## Data Flow

1. Login or registration stores access and refresh tokens in the existing session object.
2. Selecting `个人中心` calls `GET /api/auth/me` through the gateway.
3. Profile mutations carry the access token; the gateway verifies it and adds `X-User-Id`; auth-service authorizes the operation against that user ID.
4. Avatar upload writes a generated file under `cloudticket.avatar-storage-dir`, updates `auth_user.avatar_filename`, and returns a cache-busted URL using the user ID and updated timestamp.
5. Password change increments `scope_version`; the current browser session is cleared, and the next login receives a fresh token with the new version.

## Testing and Acceptance

- Backend unit tests cover profile mapping, nickname trimming, avatar MIME/size rejection, safe generated filenames, current-password verification, and password-policy rejection.
- Controller tests cover trusted user extraction, profile update responses, multipart validation, password-change response, and avatar 404 behavior.
- Frontend tests cover profile API request shapes, multipart upload, profile navigation, profile rendering, nickname save, password-change logout, and avatar fallback.
- Existing auth, logout, admin, booking, payment, and order tests remain green.
- `mvn test` and `npm test` must pass; `npm run build` must complete successfully.

## Non-goals

- Email or SMS verification.
- Phone/email rebinding.
- Remote object storage, image cropping, moderation, or CDN integration.
- Account deletion.

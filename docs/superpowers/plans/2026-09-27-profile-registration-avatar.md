# Registration and Profile Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete registration and add an authenticated profile center with avatar upload, nickname editing, password change, and logout across the auth service, gateway, and Vue web app.

**Architecture:** Auth-service remains the source of truth for `auth_user`, profile mutations are authorized from the gateway-provided `X-User-Id`, and avatar files are stored under a configurable local directory while MySQL stores only a generated filename. The Vue app keeps the existing shared session and API refresh behavior, adds a profile screen beside orders/admin, and clears the session after password changes.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, Flyway, Spring Security, Spring Cloud Gateway, MySQL, Vue 3, TypeScript, Vitest, Vue Test Utils.

**Spec:** `docs/superpowers/specs/2026-09-27-profile-registration-avatar-design.md`

## Global Constraints

- Avatar accepts JPEG, PNG, or WebP only and is limited to 2 MiB.
- Avatar filenames are UUID-generated; client filenames and paths are never persisted or used as storage paths.
- Passwords use the existing 8-128 character upper/lower/digit policy.
- Phone and email remain read-only in the profile center.
- Protected profile requests rely on the gateway JWT filter and trusted `X-User-Id` header.
- Preserve unrelated uncommitted user changes in documentation and logs.

---

### Task 1: Add profile schema and entity fields

**Files:**
- Create: `services/auth-service/src/main/resources/db/migration/V4__user_profile.sql`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/persistence/entity/AuthUserEntity.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/persistence/mapper/AuthUserMapper.java`
- Test: `services/auth-service/src/test/java/com/cloudticket/auth/AuthSchemaTest.java`

**Interfaces:**
- Produces `AuthUserEntity.avatarFilename` and mapper methods `updateNickname(UUID,String)`, `updateAvatarFilename(UUID,String)`, and `selectProfile(UUID)` for later service/controller tasks.

- [ ] **Step 1: Write the failing schema/entity test**

Add assertions that migration text contains `avatar_filename VARCHAR(255) NULL`, the entity exposes `getAvatarFilename`/`setAvatarFilename`, and the mapper declares explicit profile updates without touching `password_hash`.

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -pl services/auth-service -Dtest=AuthSchemaTest test`

Expected: FAIL because migration, field, and mapper methods do not exist.

- [ ] **Step 3: Implement the migration and persistence fields**

Create `V4__user_profile.sql` with `ALTER TABLE auth_user ADD COLUMN avatar_filename VARCHAR(255) NULL AFTER nickname;`. Add the entity property and getter/setter. Add mapper statements:

```java
@Select("SELECT * FROM auth_user WHERE id = #{id}")
AuthUserEntity selectProfile(@Param("id") UUID id);

@Update("UPDATE auth_user SET nickname = #{nickname} WHERE id = #{id}")
int updateNickname(@Param("id") UUID id, @Param("nickname") String nickname);

@Update("UPDATE auth_user SET avatar_filename = #{filename} WHERE id = #{id}")
int updateAvatarFilename(@Param("id") UUID id, @Param("filename") String filename);
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `mvn -pl services/auth-service -Dtest=AuthSchemaTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/auth-service/src/main/resources/db/migration/V4__user_profile.sql services/auth-service/src/main/java/com/cloudticket/auth/persistence/entity/AuthUserEntity.java services/auth-service/src/main/java/com/cloudticket/auth/persistence/mapper/AuthUserMapper.java services/auth-service/src/test/java/com/cloudticket/auth/AuthSchemaTest.java
git commit -m "feat(auth): add user profile persistence"
```

### Task 2: Implement profile service and avatar storage

**Files:**
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/profile/AvatarStorage.java`
- Create: `services/auth-service/src/main/java/com/cloudticket/auth/profile/LocalAvatarStorage.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/service/AuthService.java`
- Modify: `services/auth-service/src/main/resources/application.yml`
- Test: `services/auth-service/src/test/java/com/cloudticket/auth/ProfileServiceTest.java`

**Interfaces:**
- `AuthService.updateNickname(UUID,String): AuthUserEntity`
- `AuthService.changePassword(UUID,String,String): void`
- `AuthService.updateAvatar(UUID, MultipartFile): AuthUserEntity`
- `AuthService.openAvatar(UUID): Optional<AvatarStorage.StoredAvatar>`
- `AvatarStorage.save(UUID, MultipartFile): String`, `open(UUID): Optional<StoredAvatar>`, and `delete(String): void`.

- [ ] **Step 1: Write failing service tests**

Cover trimmed nickname persistence, blank/over-120 nickname rejection, wrong current password rejection, weak new password rejection, successful password update and scope-version bump, MIME/size rejection, generated UUID filename, and avatar replacement cleanup.

- [ ] **Step 2: Run the focused tests to verify failure**

Run: `mvn -pl services/auth-service -Dtest=ProfileServiceTest test`

Expected: FAIL because service methods and storage do not exist.

- [ ] **Step 3: Implement the storage abstraction and local adapter**

Use `@Value("${cloudticket.avatar-storage-dir:./data/avatars}") Path root`, create directories on construction, copy bytes to a UUID plus normalized extension using `Files.copy(..., CREATE_NEW)`, and return a `StoredAvatar(Path path, String contentType)`. Validate `MultipartFile.getSize()` and `getContentType()` before writing. Never concatenate the original filename into the path.

- [ ] **Step 4: Implement profile service methods**

Normalize nickname with `trim()`, map empty nickname to `null`, enforce 120 characters, load the user or throw `InvalidCredentialsException`, compare the current password through the existing encoder, call `PasswordPolicy.requireValid`, update password with `users.resetPassword`, and delegate avatar replacement to storage before updating the database. Delete the old generated file only after the database update succeeds.

- [ ] **Step 5: Run the focused tests to verify success**

Run: `mvn -pl services/auth-service -Dtest=ProfileServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/auth-service/src/main/java/com/cloudticket/auth/profile services/auth-service/src/main/java/com/cloudticket/auth/service/AuthService.java services/auth-service/src/main/resources/application.yml services/auth-service/src/test/java/com/cloudticket/auth/ProfileServiceTest.java
git commit -m "feat(auth): implement profile and avatar services"
```

### Task 3: Expose authenticated profile APIs and public avatar streaming

**Files:**
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/api/AuthDtos.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/api/AuthController.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/api/AuthExceptionHandler.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/persistence/entity/AuthUserEntity.java`
- Modify: `services/gateway-service/src/main/java/com/cloudticket/gateway/security/AnonymousPathPolicy.java`
- Test: `services/auth-service/src/test/java/com/cloudticket/auth/AuthControllerTest.java`
- Test: `services/gateway-service/src/test/java/com/cloudticket/gateway/AnonymousPathPolicyTest.java`

**Interfaces:**
- `GET /api/auth/me`
- `PATCH /api/auth/me` body `{ "nickname": "..." }`
- `POST /api/auth/me/avatar` multipart field `file`
- `POST /api/auth/me/password` body `{ "currentPassword": "...", "newPassword": "..." }`
- `GET /api/auth/avatars/{userId}` public image response.

- [ ] **Step 1: Write failing controller and gateway tests**

Assert trusted user extraction for GET/PATCH/password/avatar routes, response `UserView` includes `avatarUrl` and `createdAt`, multipart rejects missing files, password change returns a successful envelope, avatar storage returns 404 when missing, and anonymous policy allows only `GET /api/auth/avatars/{uuid}` while protected profile mutation paths remain disallowed anonymously.

- [ ] **Step 2: Run focused tests to verify failure**

Run: `mvn -pl services/auth-service,services/gateway-service -Dtest=AuthControllerTest,AnonymousPathPolicyTest test`

Expected: FAIL because DTO fields/routes and policy entries do not exist.

- [ ] **Step 3: Add DTOs and controller methods**

Extend `UserView` with `avatarUrl` and `createdAt`; add `ProfileUpdateRequest` and `PasswordChangeRequest` with validation. Resolve the user ID exactly as the existing `/me` method. Build `avatarUrl` as `/api/auth/avatars/{id}` when an avatar filename exists, otherwise `null`. For multipart upload use `@RequestPart("file") MultipartFile file`.

- [ ] **Step 4: Add exception mapping and public avatar response**

Map `NoSuchFileException`/missing storage to 404 and keep invalid image/password inputs at 422. Stream the avatar with `ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.contentType()))`. Do not expose filesystem paths or client filenames.

- [ ] **Step 5: Allow only public avatar GETs through the gateway policy**

Add a UUID-shaped `GET /api/auth/avatars/...` rule. Keep `/api/auth/me` protected because the gateway filter requires a bearer token for it.

- [ ] **Step 6: Run focused tests to verify success**

Run: `mvn -pl services/auth-service,services/gateway-service -Dtest=AuthControllerTest,AnonymousPathPolicyTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/auth-service/src/main/java/com/cloudticket/auth/api services/auth-service/src/main/java/com/cloudticket/auth/persistence/entity/AuthUserEntity.java services/gateway-service/src/main/java/com/cloudticket/gateway/security/AnonymousPathPolicy.java services/auth-service/src/test/java/com/cloudticket/auth/AuthControllerTest.java services/gateway-service/src/test/java/com/cloudticket/gateway/AnonymousPathPolicyTest.java
git commit -m "feat(auth): expose profile and avatar endpoints"
```

### Task 4: Add typed frontend profile API and view tests

**Files:**
- Modify: `web/src/auth/authApi.ts`
- Create: `web/src/views/ProfileView.vue`
- Modify: `web/src/styles.css`
- Test: `web/src/__tests__/profile-api.test.ts`
- Test: `web/src/__tests__/profile-view.test.ts`

**Interfaces:**
- `UserProfile = { id, phone, email, nickname, status, avatarUrl, createdAt }`
- `getProfile(): Promise<UserProfile>`
- `updateProfile(nickname): Promise<UserProfile>`
- `uploadAvatar(file): Promise<UserProfile>`
- `changePassword(currentPassword,newPassword): Promise<void>`

- [ ] **Step 1: Write failing API and component tests**

Verify GET/PATCH payloads, multipart body and absence of a manually forced `Content-Type`, password payload, profile loading state, nickname save feedback, avatar file input, initials fallback, and password success clearing the session through the parent callback.

- [ ] **Step 2: Run frontend focused tests to verify failure**

Run: `npm test -- --run web/src/__tests__/profile-api.test.ts web/src/__tests__/profile-view.test.ts`

Expected: FAIL because profile API helpers and component do not exist.

- [ ] **Step 3: Implement typed API helpers**

Use the shared `api` helper for JSON calls. For avatar upload create `FormData`, append `file`, and call `api('/api/auth/me/avatar', { method: 'POST', body: form })` without setting `Content-Type` so the browser supplies the boundary. Normalize API envelopes through the existing `unpack` path.

- [ ] **Step 4: Implement `ProfileView.vue`**

Load on mount, render avatar image or first character of nickname/email, show read-only account fields, save nickname, validate image type/size before upload, and submit current/new password. Emit `back` and `passwordChanged` events. Use alert text for request failures and disable each active form independently.

- [ ] **Step 5: Add focused styles**

Add profile grid, avatar frame, profile form, readonly field, and responsive mobile rules consistent with existing dark panels and button classes.

- [ ] **Step 6: Run focused tests to verify success**

Run: `npm test -- --run web/src/__tests__/profile-api.test.ts web/src/__tests__/profile-view.test.ts`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add web/src/auth/authApi.ts web/src/views/ProfileView.vue web/src/styles.css web/src/__tests__/profile-api.test.ts web/src/__tests__/profile-view.test.ts
git commit -m "feat(web): add profile center and avatar upload"
```

### Task 5: Wire profile navigation and password-change logout

**Files:**
- Modify: `web/src/App.vue`
- Modify: `web/src/__tests__/logout.test.ts`
- Create: `web/src/__tests__/profile-navigation.test.ts`

**Interfaces:**
- `App.vue` owns `profile` boolean state and renders `<ProfileView @back ... @passwordChanged ...>`.

- [ ] **Step 1: Write failing navigation tests**

Mount the app with an authenticated session, click `个人中心`, assert the profile view appears and orders/admin panes close, then emit password change and assert the login view returns with local tokens cleared.

- [ ] **Step 2: Run focused tests to verify failure**

Run: `npm test -- --run web/src/__tests__/profile-navigation.test.ts`

Expected: FAIL because the navigation button, state, and view wiring do not exist.

- [ ] **Step 3: Implement App wiring**

Import `ProfileView`, add `profile` state, add the signed-in navigation button, make each pane mutually exclusive, and handle `passwordChanged` by clearing the local UI session state after the API helper has revoked the server refresh token if applicable.

- [ ] **Step 4: Run focused tests to verify success**

Run: `npm test -- --run web/src/__tests__/profile-navigation.test.ts`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/App.vue web/src/__tests__/logout.test.ts web/src/__tests__/profile-navigation.test.ts
git commit -m "feat(web): wire profile navigation"
```

### Task 6: Run complete verification and fix regressions

**Files:**
- Modify: any implementation/test file needed to resolve verified failures only.

- [ ] **Step 1: Run all frontend tests**

Run: `npm test`

Expected: all Vitest tests pass with zero failures.

- [ ] **Step 2: Run frontend production build**

Run: `npm run build`

Expected: Vite exits with code 0 and produces the production bundle.

- [ ] **Step 3: Run all backend tests**

Run: `mvn test`

Expected: Maven exits with code 0 and all modules pass.

- [ ] **Step 4: Check diff and repository status**

Run: `git diff --check; git status --short`

Expected: no whitespace errors; only intended feature files plus pre-existing user modifications are present.

- [ ] **Step 5: Commit any verified fixes**

```bash
git add <only-files-changed-for-verified-fixes>
git commit -m "test: verify registration and profile center"
```


# Task 2 report

## Changes

- Added `PasswordPolicy` enforcing 8–128 characters, upper/lower case and digit.
- Added HMAC-SHA256 JWT `TokenService`, SHA-256 refresh-token digests, rotation, logout and refresh-family replay revocation.
- Added `AuthService` registration, normalized phone/email login, BCrypt verification, failed-login counter/15-minute lockout, password reset and `/me` lookup.
- Added `AuthDtos`, `AuthController` endpoint contract and centralized 401/409/422 exception mapping.
- Added Spring beans for BCrypt and token service.

## Verification

- `mvn -q -pl services/auth-service -am test` — PASS (exit 0).
- `mvn -q -pl services/auth-service test` — could not resolve reactor dependency `common-web` when run without `-am` (environment/build invocation limitation).

## Design notes and risks

JWT signing key currently defaults to a development value and should be supplied through deployment configuration. Role/permission claims are currently empty until role repositories are wired in a later task. Password-reset delivery is intentionally a generic response and in-memory token map; production should use a persisted, expiring, one-time reset-token store and notification provider. Live MySQL integration tests were not available in this environment.

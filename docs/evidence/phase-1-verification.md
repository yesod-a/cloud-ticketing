# Phase 1 verification

This record captures the reproducible local verification for the Gateway → Activity → Inventory → Order slice. It is intentionally explicit about what is and is not implemented.

## Environment

- Windows PowerShell 7.6.5
- Docker Compose stack with MySQL 8.4, Redis 7.4, Kafka 3.9, Nacos 2.5.1
- Gateway: `http://127.0.0.1:8080`
- Web: `http://127.0.0.1:5173`
- MySQL host mapping: `3308 -> 3306`

## Verification commands and results

```powershell
mvn --% -q -Dsurefire.failIfNoSpecifiedTests=false test
# PASS (reactor exit code 0; expected Mockito agent and intentional Outbox failure-test warnings)

Push-Location web
npm test -- --run
# PASS, 10 files / 16 tests
npm run build
# PASS, Vite production build
Pop-Location

docker compose config -q
git diff --check
# PASS; Git only reports LF/CRLF normalization warnings

docker compose build auth-service activity-service inventory-service order-service gateway web
# PASS, all six application images built

./tests/auth/AuthFlow.ps1
# PASS, registration/login/me/refresh/logout flow
./tests/security/TokenReplay.ps1
# PASS, replayed refresh token and descendants return 401
./tests/e2e/MultiActivityOrderFlow.ps1
# PASS, two real published activities and a persisted order
./tests/auth/AuthorizationMatrix.ps1
# PASS, anonymous admin request 401 and ordinary-user request 403
./tests/load/SeatLockLoadTest.ps1
# PASS, attempts=100 success=1 conflicts=99 p50Ms=57.27 p95Ms=134.61
```

The load run used one available database-backed seat and cancelled the single winning order after measurement, leaving the seat available again. It is a local contention probe, not a production throughput or latency claim.

## Failure and recovery coverage

- Order creation is transactional with durable outbox rows; publisher failure bookkeeping and expiry-release behavior are covered by `OrderOutboxPublisherTest`, `OrderExpiryServiceTest`, and the Order service integration checks.
- `processed_event` uniqueness is covered as a consumer idempotency contract. No concrete Payment/Ticket consumer is enabled yet.
- User ownership, resource Scope filtering, refund review Scope checks, internal inventory tokens, refresh-family replay invalidation, and `/me` service-boundary protection are covered by focused tests and Compose smoke scripts.

## Explicit limits

Payment provider callbacks, ticket issuance, reconciliation workers, and runtime Kafka retry/DLQ consumers are not implemented in Phase 1. Their versioned event and reconciliation contracts are documented under `docs/contracts/`; this evidence does not claim those services exist.

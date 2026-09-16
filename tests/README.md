# Compose integration checks

These scripts exercise the running Docker Compose stack through Gateway. Start the stack first with `docker compose up -d`.

```powershell
./tests/auth/AuthFlow.ps1
./tests/security/TokenReplay.ps1
./tests/e2e/MultiActivityOrderFlow.ps1
./tests/auth/AuthorizationMatrix.ps1
./tests/load/SeatLockLoadTest.ps1
```

Set `$env:CLOUDTICKET_BASE_URL` to target another Gateway URL. The scripts use only API responses and database-backed IDs; they do not seed or fabricate business data.

`SeatLockLoadTest.ps1` sends 100 concurrent attempts for one real available seat, expects exactly one successful lock and 99 conflicts, records p50/p95 request time, then cancels the winner for cleanup.

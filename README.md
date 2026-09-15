# Cloud Ticketing

Spring Boot 3.5.5 / Java 21 ticketing microservices foundation.

## Local dependencies

Copy `.env.example` to `.env`, then run `docker compose up -d`. Services are organized as Gateway, Activity, Inventory, and Order modules. The first phase does not include real payment, ticket issuance, or reconciliation.

## Build

```bash
mvn -q -DskipTests compile
docker compose config
```
# Cloud Ticketing

## Local verification

Run `docker compose config` to inspect the local stack, then `docker compose up -d --build` to build and start it. The web client is exposed on `http://localhost:5173` and proxies `/api/` to Gateway on port `8080`.

Set `AUTH_JWT_SIGNING_KEY` to a strong deployment secret before use outside local development. See `docs/evidence/auth-rbac-multi-activity-verification.md` for recorded verification limits.

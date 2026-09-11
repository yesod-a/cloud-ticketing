# Cloud Ticketing

Spring Boot 3.5.5 / Java 21 ticketing microservices foundation.

## Local dependencies

Copy `.env.example` to `.env`, then run `docker compose up -d`. Services are organized as Gateway, Activity, Inventory, and Order modules. The first phase does not include real payment, ticket issuance, or reconciliation.

## Build

```bash
mvn -q -DskipTests compile
docker compose config
```

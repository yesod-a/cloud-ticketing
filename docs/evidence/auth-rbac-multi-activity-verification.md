# Authentication, RBAC, and Multi-Activity Verification

| Check | Command | Result |
|---|---|---|
| Auth and service unit tests | `mvn -q -pl services/auth-service,services/gateway-service,services/activity-service,services/order-service,services/inventory-service -am test` | PASS |
| Gateway JWT tests | `mvn -q -pl services/gateway-service -am -Dtest=JwtAuthenticationFilterTest,AnonymousPathPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS |
| Activity catalog test | `mvn -q -pl services/activity-service -am -Dtest=ActivityCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS |
| Authorization policy tests | `mvn -q -pl services/order-service,services/inventory-service -am -Dtest=OrderAuthorizationTest,InventoryAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS |
| Web tests | `web/node_modules/.bin/vitest.cmd run` | PASS, 5 tests |
| Web production build | `web/node_modules/.bin/vite.cmd build` | PASS |
| Compose syntax | `docker compose config` | PASS |
| Container images | `docker compose build auth-service gateway web` | PASS |

## Coverage and limits

- Auth tests cover duplicate credentials, inactive-user login rejection, validation, and response fields. JWT tests cover HMAC verification, expiration, and anonymous-route policy.
- Public activity service seeds two published activities: `星河现场 · 城市之声` and `海岸线音乐节`.
- Docker images were built, but the complete Compose stack was not started in this verification. No live MySQL/Flyway, Redis lock, Kafka recovery, Nacos registration, cross-service discovery, or browser end-to-end test is claimed.
- Payment, ticket issuance, refunds, and reconciliation remain outside the implemented scope.

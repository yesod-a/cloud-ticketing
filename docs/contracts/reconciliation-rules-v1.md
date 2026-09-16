# Reconciliation Rules v1

The reconciliation service compares independently owned facts without writing another service's tables directly. Current local deployments do not run this service; these rules define the future repair boundary.

## Authoritative fields

| Fact | Authoritative source |
|---|---|
| Order status and order seats | `order-service` database |
| Durable lock status and expiry | `inventory-service` database |
| Temporary lock key/value and TTL | Redis, only for active concurrency state |
| Payment intent and callback result | `payment-service` database |
| Ticket existence and code status | `ticket-service` database |

Redis is never used to invent a durable order or ticket. A durable row is required before a lock or event is considered committed.

## Mismatch classes

- `ORDER_LOCK_MISSING`: pending order has no matching active inventory lock.
- `ORPHAN_LOCK`: inventory lock has no pending/paid order.
- `REDIS_DB_DIVERGENCE`: Redis key and durable lock disagree on owner, status, or expiry.
- `PAID_TICKET_MISSING`: paid order has no ticket after the issuance SLA.
- `DUPLICATE_TICKET`: more than one ticket exists for one order.
- `PAYMENT_ORDER_DIVERGENCE`: payment terminal state conflicts with the order terminal state.

Each difference stores a stable fingerprint, first/last observed timestamps, trace ID, and current repair status. Re-scanning the same unchanged mismatch is idempotent.

## Automatic repair limits

The service may automatically:

1. release an orphan Redis key when the durable lock is absent and no order is pending;
2. recreate a missing Redis key from an unexpired active durable lock;
3. requeue a ticket issuance event when an order is paid and no ticket exists;
4. mark a stale pending order for the existing order timeout job.

Automatic repair must use the owning service's authenticated command endpoint and record the command event. It must never directly update another service's tables.

## Manual review conditions

Require an operator decision for duplicate tickets, payment/order terminal conflicts, an expired lock with a paid order, repeated repair failure, or any mismatch involving an unknown event schema. Manual actions require a reason, actor, trace ID, and before/after snapshot.

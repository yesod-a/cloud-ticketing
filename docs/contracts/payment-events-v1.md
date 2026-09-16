# Payment Events v1

This document defines the compatibility contract between a future payment service and `order-service`. It is a document-only contract; no payment provider or payment runtime is enabled by the current Compose stack.

## Events

Both events use the shared `EventEnvelope` and are published on `ticket.order-events.v1` with `aggregateType=ORDER` and the order ID as the Kafka key.

### `PaymentSucceeded`

```json
{
  "eventId": "uuid",
  "eventType": "PaymentSucceeded",
  "aggregateType": "ORDER",
  "aggregateId": "order-uuid",
  "occurredAt": "2026-01-01T12:00:00Z",
  "schemaVersion": 1,
  "traceId": "trace-id",
  "payload": {
    "orderId": "order-uuid",
    "paymentId": "payment-uuid",
    "amountMinor": 19900,
    "currency": "CNY",
    "provider": "sandbox",
    "providerTransactionId": "provider-tx-id"
  }
}
```

### `PaymentFailed`

The payload contains `orderId`, `paymentId`, `reasonCode`, and a non-sensitive `message`. Provider credentials, card data, and raw callback bodies must never be placed in the event.

## Order transition rules

- `PENDING` (or the deployed equivalent `PENDING_PAYMENT`) accepts `PaymentSucceeded` and transitions once to `PAID`.
- `PENDING` accepts `PaymentFailed` and transitions to `CANCELLED` or the configured payment-failed terminal state.
- `EXPIRED`, `CANCELLED`, `REFUNDED`, and `TICKETED` reject a late success; the consumer acknowledges the event and records a conflict for reconciliation.
- `PAID` receiving the same success event is an idempotent no-op.

## Idempotency and callbacks

- The provider callback key is unique in `payment_callback`; a duplicate callback returns the previously recorded result.
- The event consumer stores `(eventId, consumerName)` in a unique processed-event table before acknowledging Kafka.
- The payment intent uses an order-level idempotency key. A different request digest for the same key is a conflict.
- Payment events are emitted through the payment service Outbox in the same transaction as the payment state update.

## Versioning

Consumers must accept the current and one previous `schemaVersion`. Additive fields are compatible; renaming or changing the meaning of existing fields requires a new event type or major topic version.

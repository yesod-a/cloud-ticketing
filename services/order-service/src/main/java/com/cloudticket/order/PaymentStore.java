package com.cloudticket.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence for the simulated payment flow: one payment intent per order. */
@Repository
public class PaymentStore {
  private final JdbcTemplate jdbc;
  public PaymentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Map<String, Object>> findOrder(String orderId) {
    return jdbc.query("SELECT id,user_id,session_id,seat_ids,status,amount_minor,created_at,updated_at FROM ticket_order WHERE id=?", this::mapOrder, orderId).stream().findFirst();
  }

  public Optional<Map<String, Object>> findPaymentByOrder(String orderId) {
    return jdbc.query("SELECT id,order_id,user_id,method,amount_minor,currency,status,qr_token,provider_transaction_id,created_at,paid_at FROM payment WHERE order_id=?", this::mapPayment, orderId).stream().findFirst();
  }

  @Transactional
  public Map<String, Object> upsertIntent(String orderId, String userId, String method, int amountMinor, String qrToken) {
    Optional<Map<String, Object>> existing = findPaymentByOrder(orderId);
    if (existing.isPresent()) {
      jdbc.update("UPDATE payment SET method=?, qr_token=? WHERE order_id=? AND status='PENDING'", method, qrToken, orderId);
      return findPaymentByOrder(orderId).orElseThrow();
    }
    jdbc.update("INSERT INTO payment(id,order_id,user_id,method,amount_minor,currency,status,qr_token) VALUES(?,?,?,?,?,'CNY','PENDING',?)",
        UUID.randomUUID().toString(), orderId, userId, method, amountMinor, qrToken);
    return findPaymentByOrder(orderId).orElseThrow();
  }

  /** Marks the payment and its order paid in one transaction and records PaymentSucceeded. */
  @Transactional
  public Map<String, Object> markPaid(String orderId, String paymentId, String providerTransactionId) {
    Map<String, Object> payment = findPaymentByOrder(orderId).orElseThrow(() -> new NoSuchElementException("payment not found"));
    if ("SUCCESS".equals(payment.get("status"))) return payment;
    int paymentChanged = jdbc.update("UPDATE payment SET status='SUCCESS', paid_at=CURRENT_TIMESTAMP, provider_transaction_id=? WHERE id=? AND status='PENDING'", providerTransactionId, paymentId);
    if (paymentChanged == 0) throw new IllegalStateException("payment is not payable");
    int orderChanged = jdbc.update("UPDATE ticket_order SET status='PAID' WHERE id=? AND status='PENDING'", orderId);
    if (orderChanged == 0) throw new IllegalStateException("order is not payable");
    writeOutbox(orderId, String.valueOf(payment.get("id")), String.valueOf(payment.get("amountMinor")), String.valueOf(payment.get("method")), providerTransactionId);
    return findPaymentByOrder(orderId).orElseThrow();
  }

  private void writeOutbox(String orderId, String paymentId, String amountMinor, String method, String providerTransactionId) {
    String payload = "{\"orderId\":\"" + jsonEscape(orderId)
        + "\",\"paymentId\":\"" + jsonEscape(paymentId)
        + "\",\"amountMinor\":" + amountMinor
        + ",\"currency\":\"CNY\",\"provider\":\"" + jsonEscape(method.toLowerCase())
        + "\",\"providerTransactionId\":\"" + jsonEscape(providerTransactionId) + "\"}";
    jdbc.update("INSERT INTO order_outbox(event_id,event_type,aggregate_type,aggregate_id,payload,trace_id,schema_version) VALUES (?,?, 'ORDER', ?,?,?,1)",
        UUID.randomUUID().toString(), "PaymentSucceeded", orderId, payload, traceId());
  }

  private static String traceId() {
    String trace = MDC.get("traceId");
    return trace == null || trace.isBlank() ? null : trace;
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private Map<String, Object> mapOrder(ResultSet r, int ignored) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", r.getString("id"));
    value.put("userId", r.getString("user_id"));
    value.put("sessionId", r.getString("session_id"));
    value.put("seatIds", r.getString("seat_ids"));
    value.put("status", r.getString("status"));
    value.put("amountMinor", r.getInt("amount_minor"));
    value.put("createdAt", r.getTimestamp("created_at").toInstant().toString());
    value.put("updatedAt", r.getTimestamp("updated_at").toInstant().toString());
    return value;
  }

  private Map<String, Object> mapPayment(ResultSet r, int ignored) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", r.getString("id"));
    value.put("orderId", r.getString("order_id"));
    value.put("userId", r.getString("user_id"));
    value.put("method", r.getString("method"));
    value.put("amountMinor", r.getInt("amount_minor"));
    value.put("currency", r.getString("currency"));
    value.put("status", r.getString("status"));
    value.put("qrToken", r.getString("qr_token"));
    value.put("providerTransactionId", r.getString("provider_transaction_id"));
    value.put("createdAt", r.getTimestamp("created_at").toInstant().toString());
    value.put("paidAt", r.getTimestamp("paid_at") == null ? null : r.getTimestamp("paid_at").toInstant().toString());
    return value;
  }
}

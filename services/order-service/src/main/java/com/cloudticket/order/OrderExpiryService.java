package com.cloudticket.order;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.client.InventoryReservationClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expires payment-window orders and releases their inventory locks. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.order-expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryService {
  private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);

  private final JdbcTemplate jdbc;
  private final InventoryReservationClient inventory;
  private final int paymentWindowMinutes;

  @Autowired
  public OrderExpiryService(
      JdbcTemplate jdbc,
      InventoryReservationClient inventory,
      @Value("${cloudticket.order-expiry.payment-window-minutes:15}") int paymentWindowMinutes) {
    this.jdbc = jdbc;
    this.inventory = inventory;
    this.paymentWindowMinutes = Math.max(1, paymentWindowMinutes);
  }

  @Scheduled(fixedDelayString = "${cloudticket.order-expiry.poll-ms:30000}")
  public void expireScheduled() {
    expirePendingOrders();
  }

  @Transactional
  public int expirePendingOrders() {
    List<Map<String, Object>> candidates = jdbc.query(
        "SELECT id,user_id,session_id,seat_ids FROM ticket_order "
            + "WHERE status='PENDING' AND created_at < TIMESTAMPADD(MINUTE, -?, CURRENT_TIMESTAMP)",
        (rs, rowNum) -> Map.of(
            "id", rs.getString("id"),
            "userId", rs.getString("user_id"),
            "sessionId", rs.getString("session_id"),
            "seatIds", rs.getString("seat_ids")),
        paymentWindowMinutes);
    int expired = 0;
    for (Map<String, Object> candidate : candidates) {
      String orderId = String.valueOf(candidate.get("id"));
      int changed = jdbc.update(
          "UPDATE ticket_order SET status='EXPIRED' WHERE id=? AND status='PENDING' "
              + "AND created_at < TIMESTAMPADD(MINUTE, -?, CURRENT_TIMESTAMP)",
          orderId, paymentWindowMinutes);
      if (changed == 0) continue; // another scanner/request won the race
      writeOutbox(candidate, orderId);
      expired++;
      if (inventory != null) {
        try {
          inventory.release(orderId);
        } catch (RuntimeException releaseFailure) {
          // Expiration is durable and idempotent; a later inventory reconciliation can retry release.
          log.warn("Inventory release failed for expired order {}", orderId, releaseFailure);
        }
      }
    }
    return expired;
  }

  private void writeOutbox(Map<String, Object> order, String orderId) {
    String payload = "{\"orderId\":\"" + jsonEscape(orderId)
        + "\",\"userId\":\"" + jsonEscape(String.valueOf(order.get("userId")))
        + "\",\"sessionId\":\"" + jsonEscape(String.valueOf(order.get("sessionId")))
        + "\",\"seatIds\":\"" + jsonEscape(String.valueOf(order.get("seatIds"))) + "\"}";
    jdbc.update(
        "INSERT INTO order_outbox(event_id,event_type,aggregate_type,aggregate_id,payload,trace_id,schema_version) VALUES (?,?, 'ORDER', ?,?,?,1)",
        UUID.randomUUID().toString(), EventTypes.ORDER_EXPIRED, orderId, payload,
        traceId());
  }

  private static String traceId() {
    String trace = MDC.get("traceId");
    return trace == null || trace.isBlank() ? null : trace;
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

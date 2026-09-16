package com.cloudticket.order;

import com.cloudticket.order.client.InventoryReservationClient;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Simulated payment: creates a QR intent per order and completes it without a real provider. */
@Service
public class PaymentService {
  private static final Set<String> METHODS = Set.of("WECHAT", "ALIPAY", "UNIONPAY");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PaymentStore store;
  private final InventoryReservationClient inventory;
  private final QrCodeGenerator qr;
  private final int paymentWindowMinutes;

  public PaymentService(PaymentStore store, QrCodeGenerator qr) { this(store, null, qr, 15); }
  @Autowired
  public PaymentService(PaymentStore store, InventoryReservationClient inventory, QrCodeGenerator qr,
                        @Value("${cloudticket.order-expiry.payment-window-minutes:15}") int paymentWindowMinutes) {
    this.store = store;
    this.inventory = inventory;
    this.qr = qr;
    this.paymentWindowMinutes = Math.max(1, paymentWindowMinutes);
  }

  public Map<String, Object> status(String orderId, String userId) {
    Map<String, Object> order = ownedOrder(orderId, userId);
    return store.findPaymentByOrder(orderId).map(payment -> view(order, payment)).orElseGet(() -> Map.of(
        "orderId", orderId, "amountMinor", order.get("amountMinor"), "currency", "CNY", "status", "NONE"));
  }

  public Map<String, Object> intent(String orderId, String userId, String method) {
    Map<String, Object> order = ownedOrder(orderId, userId);
    requirePayable(order);
    String selected = normalizeMethod(method);
    Map<String, Object> payment = store.upsertIntent(orderId, userId, selected, ((Number) order.get("amountMinor")).intValue(), randomToken());
    return view(order, payment);
  }

  public Map<String, Object> pay(String orderId, String userId) {
    Map<String, Object> order = ownedOrder(orderId, userId);
    Map<String, Object> existing = store.findPaymentByOrder(orderId).orElseThrow(() -> new IllegalStateException("payment intent required"));
    if ("SUCCESS".equals(existing.get("status"))) return view(order, existing);
    requirePayable(order);
    Map<String, Object> paid = store.markPaid(orderId, String.valueOf(existing.get("id")), "FAKE-" + UUID.randomUUID());
    if (inventory != null) inventory.confirm(orderId);
    Map<String, Object> refreshed = store.findOrder(orderId).orElse(order);
    return view(refreshed, paid);
  }

  private Map<String, Object> ownedOrder(String orderId, String userId) {
    Map<String, Object> order = store.findOrder(orderId).orElseThrow(() -> new NoSuchElementException("order not found"));
    if (userId == null || !userId.equals(String.valueOf(order.get("userId")))) throw new SecurityException("forbidden");
    return order;
  }

  private void requirePayable(Map<String, Object> order) {
    if (!"PENDING".equals(order.get("status"))) throw new IllegalStateException("order is not payable");
  }

  private static String normalizeMethod(String method) {
    String value = method == null ? "" : method.trim().toUpperCase();
    return METHODS.contains(value) ? value : "WECHAT";
  }

  private static String randomToken() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private Map<String, Object> view(Map<String, Object> order, Map<String, Object> payment) {
    String orderId = String.valueOf(order.get("id"));
    String status = String.valueOf(payment.get("status"));
    String method = String.valueOf(payment.getOrDefault("method", "WECHAT"));
    int amountMinor = ((Number) payment.getOrDefault("amountMinor", 0)).intValue();
    String token = String.valueOf(payment.getOrDefault("qrToken", ""));
    String qrContent = "cloudticket://pay?order=" + orderId + "&method=" + method + "&token=" + token;
    Instant expiresAt = Instant.parse(String.valueOf(order.get("createdAt"))).plus(paymentWindowMinutes, ChronoUnit.MINUTES);
    java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
    value.put("paymentId", payment.get("id"));
    value.put("orderId", orderId);
    value.put("method", method);
    value.put("amountMinor", amountMinor);
    value.put("currency", payment.getOrDefault("currency", "CNY"));
    value.put("status", status);
    value.put("orderStatus", order.get("status"));
    value.put("seatCount", String.valueOf(order.getOrDefault("seatIds", "")).isBlank() ? 0 : String.valueOf(order.get("seatIds")).split(",").length);
    value.put("qrContent", qrContent);
    value.put("qrCode", qr.toDataUrl(qrContent));
    value.put("expiresAt", expiresAt.toString());
    value.put("providerTransactionId", payment.get("providerTransactionId"));
    return value;
  }
}

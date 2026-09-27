package com.cloudticket.order;

import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.payment.PaymentChannel;
import com.cloudticket.order.payment.PaymentChannelRegistry;
import com.cloudticket.order.persistence.PaymentRepository;
import com.cloudticket.order.persistence.entity.PaymentEntity;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Simulated payment: creates a QR intent per order and completes it without a real provider. */
@Service
public class PaymentService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PaymentRepository store;
  private final InventoryReservationClient inventory;
  private final QrCodeGenerator qr;
  private final PaymentChannelRegistry channels;
  private final int paymentWindowMinutes;

  public PaymentService(PaymentRepository store, QrCodeGenerator qr, PaymentChannelRegistry channels) {
    this(store, null, qr, channels, 15);
  }

  @Autowired
  public PaymentService(PaymentRepository store, InventoryReservationClient inventory, QrCodeGenerator qr,
                        PaymentChannelRegistry channels,
                        @Value("${cloudticket.order-expiry.payment-window-minutes:15}") int paymentWindowMinutes) {
    this.store = store;
    this.inventory = inventory;
    this.qr = qr;
    this.channels = channels;
    this.paymentWindowMinutes = Math.max(1, paymentWindowMinutes);
  }

  public Map<String, Object> status(String orderId, String userId) {
    TicketOrderEntity order = ownedOrder(orderId, userId);
    return store.findPaymentByOrder(orderId).map(payment -> view(order, payment)).orElseGet(() -> Map.of(
        "orderId", orderId, "amountMinor", order.getAmountMinor(), "currency", "CNY", "status", "NONE"));
  }

  public Map<String, Object> intent(String orderId, String userId, String method) {
    TicketOrderEntity order = ownedOrder(orderId, userId);
    requirePayable(order);
    PaymentChannel channel = channels.resolve(method);
    PaymentEntity payment = store.upsertIntent(orderId, userId, channel.method(), order.getAmountMinor(), randomToken());
    return view(order, payment);
  }

  public Map<String, Object> pay(String orderId, String userId) {
    TicketOrderEntity order = ownedOrder(orderId, userId);
    PaymentEntity existing = store.findPaymentByOrder(orderId)
        .orElseThrow(() -> new IllegalStateException("payment intent required"));
    if ("SUCCESS".equals(existing.getStatus())) return view(order, existing);
    requirePayable(order);
    PaymentChannel channel = channels.resolve(existing.getMethod());
    PaymentEntity paid = store.markPaid(orderId, existing.getId(), channel.newTransactionId());
    if (inventory != null) inventory.confirm(orderId);
    TicketOrderEntity refreshed = store.findOrder(orderId).orElse(order);
    return view(refreshed, paid);
  }

  private TicketOrderEntity ownedOrder(String orderId, String userId) {
    TicketOrderEntity order = store.findOrder(orderId)
        .orElseThrow(() -> new NoSuchElementException("order not found"));
    if (userId == null || !userId.equals(order.getUserId())) throw new SecurityException("forbidden");
    return order;
  }

  private void requirePayable(TicketOrderEntity order) {
    if (!"PENDING".equals(order.getStatus())) throw new IllegalStateException("order is not payable");
  }

  private static String randomToken() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private Map<String, Object> view(TicketOrderEntity order, PaymentEntity payment) {
    String orderId = order.getId();
    String method = payment.getMethod() == null ? "WECHAT" : payment.getMethod();
    PaymentChannel channel = channels.resolve(method);
    String qrContent = channel.qrContent(orderId, payment.getQrToken());
    Instant expiresAt = order.getCreatedAt().plus(paymentWindowMinutes, ChronoUnit.MINUTES);
    LinkedHashMap<String, Object> value = new LinkedHashMap<>();
    value.put("paymentId", payment.getId());
    value.put("orderId", orderId);
    value.put("method", method);
    value.put("amountMinor", payment.getAmountMinor());
    value.put("currency", payment.getCurrency() == null ? "CNY" : payment.getCurrency());
    value.put("status", payment.getStatus());
    value.put("orderStatus", order.getStatus());
    value.put("seatCount", order.getSeatIds() == null || order.getSeatIds().isBlank()
        ? 0 : order.getSeatIds().split(",").length);
    value.put("qrContent", qrContent);
    value.put("qrCode", qr.toDataUrl(qrContent));
    value.put("expiresAt", expiresAt.toString());
    value.put("providerTransactionId", payment.getProviderTransactionId());
    return value;
  }
}

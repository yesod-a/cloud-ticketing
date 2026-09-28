package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.payment.AlipayPaymentChannel;
import com.cloudticket.order.payment.PaymentChannelRegistry;
import com.cloudticket.order.payment.UnionPayPaymentChannel;
import com.cloudticket.order.payment.WeChatPaymentChannel;
import com.cloudticket.order.persistence.PaymentRepository;
import com.cloudticket.order.persistence.UserSessionPurchaseRepository;
import com.cloudticket.order.persistence.entity.PaymentEntity;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

  private final PaymentRepository store = mock(PaymentRepository.class);
  private final InventoryReservationClient inventory = mock(InventoryReservationClient.class);
  private final UserSessionPurchaseRepository purchases = mock(UserSessionPurchaseRepository.class);
  private final QrCodeGenerator qr = mock(QrCodeGenerator.class);
  private final PaymentChannelRegistry channels = new PaymentChannelRegistry(
      List.of(new WeChatPaymentChannel(), new AlipayPaymentChannel(), new UnionPayPaymentChannel()),
      new WeChatPaymentChannel());

  private PaymentService service() {
    when(qr.toDataUrl(anyString())).thenReturn("data:image/png;base64,AAA");
    return new PaymentService(store, inventory, purchases, qr, channels, 15);
  }

  @Test
  void intentRejectsOrderThatIsNotPending() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PAID")));

    assertThrows(IllegalStateException.class, () -> service().intent("order-1", "user-1", "WECHAT"));
    verify(store, never()).upsertIntent(anyString(), anyString(), anyString(), anyInt(), anyString());
  }

  @Test
  void intentRejectsAnotherUsersOrder() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PENDING")));

    assertThrows(SecurityException.class, () -> service().intent("order-1", "intruder", "WECHAT"));
  }

  @Test
  void intentReturnsQrCodeAndAmountSnapshot() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PENDING")));
    when(store.upsertIntent(eq("order-1"), eq("user-1"), eq("ALIPAY"), eq(20_000), anyString()))
        .thenReturn(payment("PENDING", "ALIPAY"));

    Map<String, Object> result = service().intent("order-1", "user-1", "ALIPAY");

    assertEquals("payment-1", result.get("paymentId"));
    assertEquals(20_000, result.get("amountMinor"));
    assertEquals(2, result.get("seatCount"));
    assertEquals("data:image/png;base64,AAA", result.get("qrCode"));
    assertTrue(String.valueOf(result.get("qrContent")).contains("token-1"));
    assertTrue(String.valueOf(result.get("qrContent")).contains("ALIPAY"));
  }

  @Test
  void unknownMethodFallsBackToTheDefaultChannel() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PENDING")));
    when(store.upsertIntent(eq("order-1"), eq("user-1"), eq("WECHAT"), eq(20_000), anyString()))
        .thenReturn(payment("PENDING", "WECHAT"));

    Map<String, Object> result = service().intent("order-1", "user-1", "bitcoin");

    assertEquals("WECHAT", result.get("method"));
  }

  @Test
  void payMarksOrderPaidAndConfirmsInventory() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PENDING")));
    when(store.findPaymentByOrder("order-1")).thenReturn(Optional.of(payment("PENDING", "WECHAT")));
    when(store.markPaid(eq("order-1"), eq("payment-1"), anyString())).thenReturn(payment("SUCCESS", "WECHAT"));

    Map<String, Object> result = service().pay("order-1", "user-1");

    assertEquals("SUCCESS", result.get("status"));
    verify(store).markPaid(eq("order-1"), eq("payment-1"), startsWith("WX-"));
    verify(inventory).confirm("order-1");
  }

  @Test
  void payConfirmsGeneralAdmissionInventoryAndPurchase() {
    TicketOrderEntity order = order("PENDING");
    order.setSeatIds("");
    order.setQuantity(2);
    order.setTicketNumbers("101,102");
    when(store.findOrder("order-1")).thenReturn(Optional.of(order));
    when(store.findPaymentByOrder("order-1")).thenReturn(Optional.of(payment("PENDING", "WECHAT")));
    when(store.markPaid(eq("order-1"), eq("payment-1"), anyString())).thenReturn(payment("SUCCESS", "WECHAT"));

    service().pay("order-1", "user-1");

    verify(inventory).confirmQuantity("order-1");
    verify(purchases).activate("order-1", "user-1", "session-1", 2);
    verify(inventory, never()).confirm("order-1");
  }

  @Test
  void payIsIdempotentWhenPaymentAlreadySucceeded() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PAID")));
    when(store.findPaymentByOrder("order-1")).thenReturn(Optional.of(payment("SUCCESS", "WECHAT")));

    Map<String, Object> result = service().pay("order-1", "user-1");

    assertEquals("SUCCESS", result.get("status"));
    verify(store, never()).markPaid(anyString(), anyString(), anyString());
    verify(inventory).confirm("order-1");
  }

  @Test
  void statusReportsNoneBeforeAnIntentExists() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PENDING")));
    when(store.findPaymentByOrder("order-1")).thenReturn(Optional.empty());

    assertEquals("NONE", service().status("order-1", "user-1").get("status"));
  }

  private static TicketOrderEntity order(String status) {
    TicketOrderEntity order = new TicketOrderEntity();
    order.setId("order-1");
    order.setUserId("user-1");
    order.setSessionId("session-1");
    order.setSeatIds("seat-1,seat-2");
    order.setStatus(status);
    order.setAmountMinor(20_000);
    order.setCreatedAt(Instant.parse("2026-09-16T00:00:00Z"));
    return order;
  }

  private static PaymentEntity payment(String status, String method) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId("payment-1");
    payment.setOrderId("order-1");
    payment.setUserId("user-1");
    payment.setMethod(method);
    payment.setAmountMinor(20_000);
    payment.setCurrency("CNY");
    payment.setStatus(status);
    payment.setQrToken("token-1");
    return payment;
  }
}

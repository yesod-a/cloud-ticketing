package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudticket.order.client.InventoryReservationClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
  private final PaymentStore store = mock(PaymentStore.class);
  private final InventoryReservationClient inventory = mock(InventoryReservationClient.class);
  private final QrCodeGenerator qr = mock(QrCodeGenerator.class);

  private PaymentService service() {
    when(qr.toDataUrl(anyString())).thenReturn("data:image/png;base64,AAA");
    return new PaymentService(store, inventory, qr, 15);
  }

  private Map<String, Object> order(String status) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", "order-1");
    value.put("userId", "user-1");
    value.put("sessionId", "session-1");
    value.put("seatIds", "seat-1,seat-2");
    value.put("status", status);
    value.put("amountMinor", 20000);
    value.put("createdAt", "2026-09-16T00:00:00Z");
    value.put("updatedAt", "2026-09-16T00:00:00Z");
    return value;
  }

  private Map<String, Object> payment(String status) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", "payment-1");
    value.put("orderId", "order-1");
    value.put("userId", "user-1");
    value.put("method", "WECHAT");
    value.put("amountMinor", 20000);
    value.put("currency", "CNY");
    value.put("status", status);
    value.put("qrToken", "token-1");
    value.put("providerTransactionId", null);
    return value;
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
    when(store.upsertIntent(eq("order-1"), eq("user-1"), eq("ALIPAY"), eq(20000), anyString())).thenReturn(payment("PENDING"));

    Map<String, Object> result = service().intent("order-1", "user-1", "ALIPAY");

    assertEquals("payment-1", result.get("paymentId"));
    assertEquals(20000, result.get("amountMinor"));
    assertEquals(2, result.get("seatCount"));
    assertEquals("data:image/png;base64,AAA", result.get("qrCode"));
    assertTrue(String.valueOf(result.get("qrContent")).contains("token-1"));
  }

  @Test
  void payMarksOrderPaidAndConfirmsInventory() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PENDING")));
    when(store.findPaymentByOrder("order-1")).thenReturn(Optional.of(payment("PENDING")));
    when(store.markPaid(eq("order-1"), eq("payment-1"), anyString())).thenReturn(payment("SUCCESS"));

    Map<String, Object> result = service().pay("order-1", "user-1");

    assertEquals("SUCCESS", result.get("status"));
    verify(inventory).confirm("order-1");
  }

  @Test
  void payIsIdempotentWhenPaymentAlreadySucceeded() {
    when(store.findOrder("order-1")).thenReturn(Optional.of(order("PAID")));
    when(store.findPaymentByOrder("order-1")).thenReturn(Optional.of(payment("SUCCESS")));

    Map<String, Object> result = service().pay("order-1", "user-1");

    assertEquals("SUCCESS", result.get("status"));
    verify(store, never()).markPaid(anyString(), anyString(), anyString());
    verify(inventory, never()).confirm(anyString());
  }
}

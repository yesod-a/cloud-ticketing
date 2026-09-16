package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.cloudticket.order.api.OrderController;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderControllerScopeTest {
  @Test
  void refundApprovalCannotCrossSessionScope() {
    OrderStore store = mock(OrderStore.class);
    when(store.refund("refund-1")).thenReturn(Optional.of(Map.of("orderId", "order-1")));
    when(store.find("order-1")).thenReturn(Optional.of(Map.of("sessionId", "session-2")));
    OrderController controller = new OrderController(store);

    assertThrows(SecurityException.class, () -> controller.approveRefund(
        "refund-1", "order:refund", "SESSION:session-1", "reviewer-1"));
    verify(store, never()).reviewRefund(anyString(), anyBoolean(), anyString());
  }
}

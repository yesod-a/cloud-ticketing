package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.order.api.OrderController;
import com.cloudticket.order.api.RefundScopeLookup;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.RefundRepository;
import com.cloudticket.order.persistence.entity.RefundRequestEntity;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderControllerScopeTest {

  private final OrderRepository orders = mock(OrderRepository.class);
  private final RefundRepository refunds = mock(RefundRepository.class);
  private final PaymentService payments = mock(PaymentService.class);

  private OrderController controller() {
    return TestAspects.authorized(new OrderController(orders, refunds, payments), mock(AuditSink.class),
        Map.of("refundScopes", new RefundScopeLookup(refunds, orders)));
  }

  @Test
  void refundApprovalCannotCrossSessionScope() {
    when(refunds.find("refund-1")).thenReturn(Optional.of(refund("refund-1", "order-1")));
    when(orders.find("order-1")).thenReturn(Optional.of(order("order-1", "session-2")));

    assertThrows(SecurityException.class, () -> asCaller("order:refund", "SESSION:session-1",
        () -> controller().approveRefund("refund-1", "reviewer-1")));
    verify(refunds, never()).review(anyString(), anyBoolean(), anyString());
  }

  @Test
  void refundApprovalSucceedsInsideTheSessionScope() {
    when(refunds.find("refund-1")).thenReturn(Optional.of(refund("refund-1", "order-1")));
    when(orders.find("order-1")).thenReturn(Optional.of(order("order-1", "session-2")));
    when(refunds.review("refund-1", true, "reviewer-1")).thenReturn(refund("refund-1", "order-1"));

    Map<String, Object> response = asCaller("order:refund", "SESSION:session-2",
        () -> controller().approveRefund("refund-1", "reviewer-1"));

    assertEquals("refund-1", response.get("id"));
    verify(refunds).review("refund-1", true, "reviewer-1");
  }

  @Test
  void refundApprovalRequiresTheRefundPermission() {
    assertThrows(SecurityException.class, () -> asCaller("order:read", "SESSION:session-2",
        () -> controller().approveRefund("refund-1", "reviewer-1")));
    verify(refunds, never()).review(anyString(), anyBoolean(), anyString());
  }

  @Test
  void adminOrderListingRequiresOrderRead() {
    assertThrows(SecurityException.class, () -> asCaller("activity:read", "",
        () -> controller().admin("", 0, 20)));
    verify(orders, never()).pageForAdmin(anyString(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString());
  }

  @Test
  void adminOrderListingPassesTheCallerScopeToTheRepository() {
    when(orders.pageForAdmin("", 0, 20, "order:read", "SESSION:session-1"))
        .thenReturn(new com.cloudticket.common.web.PageResult<>(java.util.List.of(), 0, 20, 0));

    Map<String, Object> response = asCaller("order:read", "SESSION:session-1",
        () -> controller().admin("", 0, 20));

    assertEquals(0L, response.get("total"));
  }

  private static <T> T asCaller(String permissions, String scopes, java.util.function.Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, scopes, "reviewer-1", "trace-1", ""), action);
  }

  private static RefundRequestEntity refund(String id, String orderId) {
    RefundRequestEntity refund = new RefundRequestEntity();
    refund.setId(id);
    refund.setOrderId(orderId);
    refund.setUserId("user-1");
    refund.setReason("changed my mind");
    refund.setStatus("REQUESTED");
    return refund;
  }

  private static TicketOrderEntity order(String id, String sessionId) {
    TicketOrderEntity order = new TicketOrderEntity();
    order.setId(id);
    order.setUserId("user-1");
    order.setSessionId(sessionId);
    order.setStatus("PAID");
    return order;
  }
}

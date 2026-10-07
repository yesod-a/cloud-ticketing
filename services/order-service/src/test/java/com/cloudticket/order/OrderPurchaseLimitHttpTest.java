package com.cloudticket.order;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.RefundRepository;
import com.cloudticket.order.persistence.UserSessionPurchaseRepository;
import com.cloudticket.order.api.OrderController;
import com.cloudticket.order.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderPurchaseLimitHttpTest {
  private final OrderRepository orders = mock(OrderRepository.class);
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new OrderController(orders, mock(RefundRepository.class), mock(PaymentService.class))).build();

  @Test
  void purchaseLimitIsReportedAsConflict() throws Exception {
    when(orders.createGeneralAdmission(anyString(), anyString(), anyInt(), anyString()))
        .thenThrow(new UserSessionPurchaseRepository.PurchaseLimitExceededException());

    mvc.perform(post("/api/orders")
            .header("X-User-Id", "user-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sessionId\":\"session-1\",\"quantity\":1,\"idempotencyKey\":\"key-1\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void mixedQuantityAndSeatRequestIsRejectedAtTheBoundary() throws Exception {
    mvc.perform(post("/api/orders")
            .header("X-User-Id", "user-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sessionId\":\"session-1\",\"quantity\":1,\"seatIds\":\"A1\",\"idempotencyKey\":\"key-1\"}"))
        .andExpect(status().isBadRequest());

    verify(orders, never()).createGeneralAdmission(anyString(), anyString(), anyInt(), anyString());
  }
}

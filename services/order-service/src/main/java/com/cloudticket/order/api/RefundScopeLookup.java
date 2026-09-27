package com.cloudticket.order.api;

import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.RefundRepository;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import org.springframework.stereotype.Component;

/**
 * Resolves the session a refund request belongs to.
 *
 * <p>{@code @RequireScope} needs the owning session id, which is not a request parameter: it has to
 * be read from the refund request and then from its order.
 */
@Component("refundScopes")
public class RefundScopeLookup {

  private final RefundRepository refunds;
  private final OrderRepository orders;

  public RefundScopeLookup(RefundRepository refunds, OrderRepository orders) {
    this.refunds = refunds;
    this.orders = orders;
  }

  public String session(String refundId) {
    return refunds.find(refundId)
        .flatMap(refund -> orders.find(refund.getOrderId()))
        .map(TicketOrderEntity::getSessionId)
        .orElse(null);
  }
}

package com.cloudticket.order;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.event.OrderExpiredPayload;
import com.cloudticket.order.event.OutboxEventWriter;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.UserSessionPurchaseRepository;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expires payment-window orders and releases their inventory locks. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.order-expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryService {
  private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);

  private final OrderRepository orders;
  private final InventoryReservationClient inventory;
  private final UserSessionPurchaseRepository purchases;
  private final OutboxEventWriter outbox;
  private final int paymentWindowMinutes;

  public OrderExpiryService(
      OrderRepository orders,
      InventoryReservationClient inventory,
      OutboxEventWriter outbox,
      @Value("${cloudticket.order-expiry.payment-window-minutes:15}") int paymentWindowMinutes) {
    this(orders, inventory, null, outbox, paymentWindowMinutes);
  }

  @Autowired
  public OrderExpiryService(OrderRepository orders, InventoryReservationClient inventory,
                            UserSessionPurchaseRepository purchases, OutboxEventWriter outbox,
                            @Value("${cloudticket.order-expiry.payment-window-minutes:15}") int paymentWindowMinutes) {
    this.orders = orders;
    this.inventory = inventory;
    this.purchases = purchases;
    this.outbox = outbox;
    this.paymentWindowMinutes = Math.max(1, paymentWindowMinutes);
  }

  @Scheduled(fixedDelayString = "${cloudticket.order-expiry.poll-ms:30000}")
  public void expireScheduled() {
    expirePendingOrders();
  }

  @Transactional
  public int expirePendingOrders() {
    int expired = 0;
    for (TicketOrderEntity candidate : orders.findExpiredCandidates(paymentWindowMinutes)) {
      String orderId = candidate.getId();
      if (!orders.expireIfStillPending(orderId, paymentWindowMinutes)) {
        continue; // another scanner or request won the race
      }
      outbox.write(EventTypes.ORDER_EXPIRED, orderId, new OrderExpiredPayload(orderId, candidate.getUserId(),
          candidate.getSessionId(), candidate.getSeatIds()));
      expired++;
      if (inventory != null) {
        try {
          boolean generalAdmission = candidate.getQuantity() != null && candidate.getQuantity() > 0;
          if (generalAdmission) {
            inventory.releaseQuantity(orderId);
            if (purchases != null) purchases.release(orderId, candidate.getUserId(), candidate.getSessionId(), candidate.getQuantity());
          } else {
            inventory.release(orderId);
          }
        } catch (RuntimeException releaseFailure) {
          // Expiration is durable and idempotent; a later inventory reconciliation can retry release.
          log.warn("Inventory release failed for expired order {}", orderId, releaseFailure);
        }
      }
      try {
        orders.releaseCouponReservation(candidate);
      } catch (RuntimeException releaseFailure) {
        log.warn("Promotion release failed for expired order {}", orderId, releaseFailure);
      }
    }
    if (purchases != null) purchases.expireReservations();
    return expired;
  }
}

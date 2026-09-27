package com.cloudticket.order.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.event.OutboxEventWriter;
import com.cloudticket.order.event.PaymentSucceededPayload;
import com.cloudticket.order.payment.PaymentChannelRegistry;
import com.cloudticket.order.persistence.entity.PaymentEntity;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import com.cloudticket.order.persistence.mapper.PaymentMapper;
import com.cloudticket.order.persistence.mapper.TicketOrderMapper;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence for the simulated payment flow: one payment intent per order. */
@Repository
public class PaymentRepository {

  private final PaymentMapper payments;
  private final TicketOrderMapper orders;
  private final InventoryReservationClient inventory;
  private final OutboxEventWriter outbox;
  private final PaymentChannelRegistry channels;

  public PaymentRepository(PaymentMapper payments, TicketOrderMapper orders,
                           InventoryReservationClient inventory, OutboxEventWriter outbox,
                           PaymentChannelRegistry channels) {
    this.payments = payments;
    this.orders = orders;
    this.inventory = inventory;
    this.outbox = outbox;
    this.channels = channels;
  }

  public Optional<TicketOrderEntity> findOrder(String orderId) {
    return Optional.ofNullable(orders.selectById(orderId));
  }

  public Optional<PaymentEntity> findPaymentByOrder(String orderId) {
    return Optional.ofNullable(payments.selectOne(
        Wrappers.<PaymentEntity>lambdaQuery().eq(PaymentEntity::getOrderId, orderId)));
  }

  @Transactional
  public PaymentEntity upsertIntent(String orderId, String userId, String method, int amountMinor,
                                    String qrToken) {
    if (findPaymentByOrder(orderId).isPresent()) {
      payments.refreshIntent(orderId, method, qrToken);
      return findPaymentByOrder(orderId).orElseThrow();
    }
    PaymentEntity intent = new PaymentEntity();
    intent.setId(UUID.randomUUID().toString());
    intent.setOrderId(orderId);
    intent.setUserId(userId);
    intent.setMethod(method);
    intent.setAmountMinor(amountMinor);
    intent.setCurrency("CNY");
    intent.setStatus("PENDING");
    intent.setQrToken(qrToken);
    payments.insert(intent);
    return findPaymentByOrder(orderId).orElseThrow();
  }

  /**
   * Marks the payment and its order paid in one transaction and records {@code PaymentSucceeded}.
   *
   * <p>Both updates are conditional, so a concurrent double payment cannot pass either guard.
   */
  @Transactional
  public PaymentEntity markPaid(String orderId, String paymentId, String providerTransactionId) {
    PaymentEntity payment = findPaymentByOrder(orderId)
        .orElseThrow(() -> new NoSuchElementException("payment not found"));
    if ("SUCCESS".equals(payment.getStatus())) return payment;
    if (payments.markPaid(paymentId, providerTransactionId) == 0) {
      throw new IllegalStateException("payment is not payable");
    }
    if (orders.markPaidIfPending(orderId) == 0) {
      throw new IllegalStateException("order is not payable");
    }
    outbox.write(EventTypes.PAYMENT_SUCCEEDED, orderId,
        new PaymentSucceededPayload(orderId, payment.getId(), payment.getAmountMinor(), payment.getCurrency(),
            channels.resolve(payment.getMethod()).provider(), providerTransactionId));
    return findPaymentByOrder(orderId).orElseThrow();
  }
}

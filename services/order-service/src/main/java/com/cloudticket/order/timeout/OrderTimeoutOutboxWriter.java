package com.cloudticket.order.timeout;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutOutboxWriter {
  private final OrderTimeoutOutboxMapper rows;

  public OrderTimeoutOutboxWriter(OrderTimeoutOutboxMapper rows) { this.rows = rows; }

  public void write(String orderId, Instant expireAt) {
    OrderTimeoutOutboxEntity row = new OrderTimeoutOutboxEntity();
    row.setId(UUID.randomUUID().toString());
    row.setOrderId(orderId);
    row.setExpireAt(expireAt);
    try {
      rows.insert(row);
    } catch (DuplicateKeyException duplicate) {
      // A retry of the same order must not create a second timeout task.
    }
  }
}

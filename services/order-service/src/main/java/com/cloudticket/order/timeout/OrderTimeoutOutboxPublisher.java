package com.cloudticket.order.timeout;

import com.cloudticket.order.OrderTimeoutZsetService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes durable timeout rows into the Redis scheduling index. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.order-timeout", name = "outbox-recovery-enabled", havingValue = "true")
public class OrderTimeoutOutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OrderTimeoutOutboxPublisher.class);
  private final OrderTimeoutOutboxMapper rows;
  private final OrderTimeoutZsetService timeouts;
  private final int batchSize;

  public OrderTimeoutOutboxPublisher(OrderTimeoutOutboxMapper rows, OrderTimeoutZsetService timeouts,
      @Value("${cloudticket.order-timeout.outbox-batch-size:100}") int batchSize) {
    this.rows = rows;
    this.timeouts = timeouts;
    this.batchSize = Math.max(1, Math.min(1000, batchSize));
  }

  @Scheduled(fixedDelayString = "${cloudticket.order-timeout.outbox-poll-ms:1000}")
  public void publishScheduled() { publishOnce(); }

  public int publishOnce() {
    int published = 0;
    List<OrderTimeoutOutboxEntity> pending = rows.pending(batchSize);
    for (OrderTimeoutOutboxEntity row : pending) {
      try {
        timeouts.schedule(row.getOrderId(), row.getExpireAt());
        rows.markPublished(row.getId());
        published++;
      } catch (RuntimeException failure) {
        rows.markFailure(row.getId(), message(failure));
        log.warn("Unable to publish order timeout {}", row.getOrderId(), failure);
      }
    }
    return published;
  }

  private static String message(RuntimeException failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }
}

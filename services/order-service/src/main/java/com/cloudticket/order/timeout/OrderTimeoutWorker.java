package com.cloudticket.order.timeout;

import com.cloudticket.order.OrderTimeoutZsetService;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims due timeout IDs from Redis and commits the order transition in MySQL. */
@Component
public class OrderTimeoutWorker {
  private static final Logger log = LoggerFactory.getLogger(OrderTimeoutWorker.class);
  private final OrderTimeoutZsetService timeouts;
  private final OrderRepository orders;
  private final int batchSize;
  private final long leaseSeconds;

  public OrderTimeoutWorker(OrderTimeoutZsetService timeouts, OrderRepository orders,
      @Value("${cloudticket.order-timeout.batch-size:100}") int batchSize,
      @Value("${cloudticket.order-timeout.lease-seconds:30}") long leaseSeconds) {
    this.timeouts = timeouts;
    this.orders = orders;
    this.batchSize = Math.max(1, Math.min(1000, batchSize));
    this.leaseSeconds = Math.max(5, leaseSeconds);
  }

  @XxlJob("orderTimeoutJobHandler")
  public void execute() {
    processDue(parseLimit(XxlJobHelper.getJobParam(), batchSize));
  }

  @Scheduled(fixedDelayString = "${cloudticket.order-timeout.poll-ms:30000}")
  public void scheduledFallback() { processDue(batchSize); }

  public int processDue(int limit) {
    long now = System.currentTimeMillis();
    timeouts.requeueExpiredClaims(now, limit);
    List<String> ids = timeouts.claimDue(limit, now, now + leaseSeconds * 1000L);
    int handled = 0;
    for (String id : ids) {
      try {
        orders.expireAndWriteEvent(id);
        timeouts.acknowledge(id);
        handled++;
      } catch (RuntimeException failure) {
        log.warn("Order timeout processing failed for {}", id, failure);
      }
    }
    return handled;
  }

  /** Rebuilds the scheduling index after Redis loss; MySQL is the durable source. */
  @XxlJob("orderTimeoutRebuildJobHandler")
  public void rebuildJob() {
    rebuild(parseLimit(XxlJobHelper.getJobParam(), batchSize));
  }

  @Scheduled(fixedDelayString = "${cloudticket.order-timeout.rebuild-poll-ms:300000}")
  public void rebuildFallback() { rebuild(batchSize); }

  public int rebuild(int limit) {
    List<TicketOrderEntity> pending = orders.findPendingForTimeoutRebuild(limit);
    for (TicketOrderEntity order : pending) {
      if (order.getExpireAt() != null) timeouts.schedule(order.getId(), order.getExpireAt());
    }
    return pending.size();
  }

  private static int parseLimit(String value, int fallback) {
    if (value == null || value.isBlank()) return fallback;
    try { return Math.max(1, Integer.parseInt(value.trim())); }
    catch (NumberFormatException ignored) { return fallback; }
  }
}

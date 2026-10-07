package com.cloudticket.inventory.reconcile;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** XXL-JOB adapter. Durable claims make retries safe across multiple inventory instances. */
@Component
public class InventoryCachePreheatJob {
  private final InventoryCachePreheatService service;
  private final int batchSize;
  @Value("${cloudticket.inventory.preheat.enabled:true}")
  private boolean enabled = true;

  public InventoryCachePreheatJob(
      InventoryCachePreheatService service,
      @Value("${cloudticket.inventory.preheat.batch-size:100}") int batchSize) {
    this.service = service;
    this.batchSize = Math.max(1, batchSize);
  }

  @XxlJob("inventoryCachePreheatJobHandler")
  public void execute() {
    if (!enabled) return;
    String triggerId = String.valueOf(XxlJobHelper.getJobId());
    String parameter = XxlJobHelper.getJobParam();
    int limit = parseLimit(parameter, batchSize);
    service.preheatDue(triggerId, limit);
  }

  public void execute(String triggerId) {
    if (!enabled) return;
    service.preheatDue(triggerId, batchSize);
  }

  private static int parseLimit(String value, int fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Math.max(1, Integer.parseInt(value.trim()));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}

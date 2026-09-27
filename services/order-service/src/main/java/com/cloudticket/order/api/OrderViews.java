package com.cloudticket.order.api;

import com.cloudticket.common.web.PageResult;
import com.cloudticket.order.persistence.entity.RefundRequestEntity;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders order rows as the JSON the clients already expect.
 *
 * <p>The row-to-map conversion used to be duplicated inside the store classes; keeping it at the API
 * boundary makes the exposed field set explicit and keeps {@code idempotencyKey} out of responses.
 */
public final class OrderViews {

  private OrderViews() {}

  public static Map<String, Object> order(TicketOrderEntity order) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", order.getId());
    value.put("userId", order.getUserId());
    value.put("sessionId", order.getSessionId());
    value.put("seatIds", order.getSeatIds());
    value.put("status", order.getStatus());
    value.put("requestHash", order.getRequestHash());
    value.put("amountMinor", order.getAmountMinor());
    value.put("createdAt", iso(order.getCreatedAt()));
    value.put("updatedAt", iso(order.getUpdatedAt()));
    return value;
  }

  public static Map<String, Object> refund(RefundRequestEntity refund) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", refund.getId());
    value.put("orderId", refund.getOrderId());
    value.put("userId", refund.getUserId());
    value.put("reason", refund.getReason());
    value.put("status", refund.getStatus());
    value.put("reviewedBy", refund.getReviewedBy() == null ? "" : refund.getReviewedBy());
    value.put("reviewedAt", iso(refund.getReviewedAt()));
    value.put("createdAt", iso(refund.getCreatedAt()));
    return value;
  }

  public static Map<String, Object> ordersPage(PageResult<TicketOrderEntity> page) {
    List<Map<String, Object>> items = page.items().stream().map(OrderViews::order).toList();
    return new PageResult<>(items, page.page(), page.size(), page.total()).asMap();
  }

  public static Map<String, Object> refundsPage(PageResult<RefundRequestEntity> page) {
    List<Map<String, Object>> items = page.items().stream().map(OrderViews::refund).toList();
    return new PageResult<>(items, page.page(), page.size(), page.total()).asMap();
  }

  private static String iso(Instant value) {
    return value == null ? null : value.toString();
  }
}

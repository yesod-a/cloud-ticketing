package com.cloudticket.order.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import com.cloudticket.order.persistence.entity.RefundRequestEntity;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import com.cloudticket.order.persistence.mapper.RefundRequestMapper;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence and review workflow for {@code refund_request}. */
@Repository
public class RefundRepository {

  private static final String REQUESTED = "REQUESTED";

  private final RefundRequestMapper refunds;
  private final OrderRepository orderRepository;

  public RefundRepository(RefundRequestMapper refunds, OrderRepository orderRepository) {
    this.refunds = refunds;
    this.orderRepository = orderRepository;
  }

  @Transactional
  public RefundRequestEntity request(String orderId, String userId, String reason) {
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("refund reason required");
    TicketOrderEntity order = orderRepository.find(orderId)
        .filter(candidate -> userId != null && userId.equals(candidate.getUserId()))
        .orElseThrow(() -> new NoSuchElementException("order not found"));
    if (!"PAID".equals(order.getStatus())) throw new IllegalStateException("order is not refundable");

    RefundRequestEntity request = new RefundRequestEntity();
    request.setId(UUID.randomUUID().toString());
    request.setOrderId(orderId);
    request.setUserId(userId);
    request.setReason(reason.trim());
    request.setStatus(REQUESTED);
    try {
      refunds.insert(request);
    } catch (DuplicateKeyException duplicate) {
      // One refund request per order: re-submitting returns the request already on file.
      return findByOrder(orderId).orElseThrow(() -> duplicate);
    }
    return find(request.getId()).orElseThrow();
  }

  public Optional<RefundRequestEntity> find(String id) {
    return Optional.ofNullable(refunds.selectById(id));
  }

  public Optional<RefundRequestEntity> findByOrder(String orderId) {
    return Optional.ofNullable(refunds.selectOne(
        Wrappers.<RefundRequestEntity>lambdaQuery().eq(RefundRequestEntity::getOrderId, orderId)));
  }

  public PageResult<RefundRequestEntity> page(String status, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<RefundRequestEntity> result = refunds.selectPage(new Page<>(safePage + 1L, safeSize),
        Wrappers.<RefundRequestEntity>lambdaQuery()
            .eq(status != null && !status.isBlank(), RefundRequestEntity::getStatus, trimmed(status))
            .orderByDesc(RefundRequestEntity::getCreatedAt));
    return new PageResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
  }

  public PageResult<RefundRequestEntity> page(String status, int page, int size,
                                              String permissions, String scopes) {
    if (ResourceScopeRule.contains(permissions, "system:config")) return page(status, page, size);
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<RefundRequestEntity> result = refunds.selectScopedPage(new Page<>(safePage + 1L, safeSize),
        trimmed(status), scopes == null ? "" : scopes.replace(" ", ""));
    return new PageResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
  }

  @Transactional
  public RefundRequestEntity review(String id, boolean approve, String reviewer) {
    RefundRequestEntity request = find(id)
        .orElseThrow(() -> new NoSuchElementException("refund request not found"));
    if (!REQUESTED.equals(request.getStatus())) {
      throw new IllegalStateException("refund request already reviewed");
    }
    String next = approve ? "APPROVED" : "REJECTED";
    if (refunds.review(id, next, reviewer) == 0) {
      throw new IllegalStateException("refund request already reviewed");
    }
    if (approve) orderRepository.markRefunded(request.getOrderId(), id, reviewer);
    return find(id).orElseThrow();
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }
}

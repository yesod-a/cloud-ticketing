package com.cloudticket.order.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.common.domain.SeatIds;
import com.cloudticket.common.events.EventTypes;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import com.cloudticket.order.IdempotencyConflictException;
import com.cloudticket.order.client.ActivitySessionClient;
import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.client.OrderIdempotencyGate;
import com.cloudticket.order.client.PromotionClient;
import com.cloudticket.order.event.OrderCancelledPayload;
import com.cloudticket.order.event.OrderCreatedPayload;
import com.cloudticket.order.event.OrderRefundedPayload;
import com.cloudticket.order.event.OutboxEventWriter;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import com.cloudticket.order.persistence.mapper.TicketOrderMapper;
import com.cloudticket.order.timeout.OrderTimeoutOutboxWriter;
import com.cloudticket.order.OrderTimeoutZsetService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence and state transitions for {@code ticket_order}. */
@Repository
public class OrderRepository {

  private final TicketOrderMapper orders;
  private final InventoryReservationClient inventory;
  private final ActivitySessionClient sessions;
  private final OutboxEventWriter outbox;
  private final UserSessionPurchaseRepository purchases;
  private final PromotionClient promotions;
  private final OrderIdempotencyGate idempotencyGate;
  private final OrderTimeoutOutboxWriter timeoutOutbox;
  private final OrderTimeoutZsetService timeoutZset;
  private final int paymentWindowMinutes;

  public OrderRepository(TicketOrderMapper orders, InventoryReservationClient inventory,
                         ActivitySessionClient sessions, OutboxEventWriter outbox) {
    this(orders, inventory, sessions, outbox, null, null, null, null, null, 15);
  }

  public OrderRepository(TicketOrderMapper orders, InventoryReservationClient inventory,
                         ActivitySessionClient sessions, OutboxEventWriter outbox,
                         UserSessionPurchaseRepository purchases) {
    this(orders, inventory, sessions, outbox, purchases, null, null, null, null, 15);
  }

  public OrderRepository(TicketOrderMapper orders, InventoryReservationClient inventory,
                         ActivitySessionClient sessions, OutboxEventWriter outbox,
                         UserSessionPurchaseRepository purchases, PromotionClient promotions) {
    this(orders, inventory, sessions, outbox, purchases, promotions, null, null, null, 15);
  }

  public OrderRepository(TicketOrderMapper orders, InventoryReservationClient inventory,
                         ActivitySessionClient sessions, OutboxEventWriter outbox,
                         OrderTimeoutZsetService timeoutZset) {
    this(orders, inventory, sessions, outbox, null, null, null, null, timeoutZset, 15);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OrderRepository(TicketOrderMapper orders, InventoryReservationClient inventory,
                         ActivitySessionClient sessions, OutboxEventWriter outbox,
                         UserSessionPurchaseRepository purchases, PromotionClient promotions,
                         OrderIdempotencyGate idempotencyGate, OrderTimeoutOutboxWriter timeoutOutbox,
                         OrderTimeoutZsetService timeoutZset,
                         @org.springframework.beans.factory.annotation.Value("${cloudticket.order-expiry.payment-window-minutes:15}") int paymentWindowMinutes) {
    this.orders = orders;
    this.inventory = inventory;
    this.sessions = sessions;
    this.outbox = outbox;
    this.purchases = purchases;
    this.promotions = promotions;
    this.idempotencyGate = idempotencyGate;
    this.timeoutOutbox = timeoutOutbox;
    this.timeoutZset = timeoutZset;
    this.paymentWindowMinutes = Math.max(1, paymentWindowMinutes);
  }

  @Transactional
  public TicketOrderEntity createGeneralAdmission(String userId, String sessionId, int quantity, String idempotencyKey) {
    return createGeneralAdmission(userId, sessionId, quantity, idempotencyKey, null);
  }

  @Transactional
  public TicketOrderEntity createGeneralAdmission(String userId, String sessionId, int quantity, String idempotencyKey, String couponId) {
    requireText(userId, "userId"); requireText(sessionId, "sessionId"); requireText(idempotencyKey, "idempotencyKey");
    if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
    String key = idempotencyKey.trim();
    String requestHash = requestHash(userId, sessionId, quantity);
    Optional<TicketOrderEntity> previous = findByIdempotencyKey(key);
    if (previous.isPresent()) return replay(previous.get(), requestHash);
    ActivitySessionClient.SessionInfo info = sessions.session(sessionId);
    requireOnSale(info);
    if (!"GENERAL_ADMISSION".equalsIgnoreCase(info.layoutMode())) throw new IllegalArgumentException("quantity only applies to general admission");
    String id = UUID.randomUUID().toString();
    int originalAmount = info.priceMinor() * quantity;
    PromotionClient.Quote coupon = promotions == null ? new PromotionClient.Quote(null, 0, originalAmount)
        : promotions.quote(id, userId, info.activityId(), sessionId, originalAmount, couponId);
    TicketOrderEntity order = new TicketOrderEntity();
    order.setId(id); order.setUserId(userId); order.setSessionId(sessionId); order.setSeatIds("");
    order.setActivityId(info.activityId()); order.setCouponId(couponId); order.setCouponReservationId(coupon.reservationId());
    order.setQuantity(quantity); order.setTicketNumbers("");
    order.setIdempotencyKey(key); order.setRequestHash(requestHash); order.setStatus("PENDING");
    order.setExpireAt(expiryDeadline());
    order.setOriginalAmountMinor(originalAmount); order.setDiscountAmountMinor(coupon.discountAmountMinor()); order.setAmountMinor(coupon.payableAmountMinor());
    scheduleTimeout(order);
    try {
      // Claim the unique idempotency key before any quota or inventory side effects. A concurrent
      // replay therefore observes the winner instead of racing into a purchase-limit conflict.
      orders.insert(order);
    } catch (DuplicateKeyException concurrentDuplicate) {
      removeTimeout(id);
      return findByIdempotencyKey(key).map(existing -> replay(existing, requestHash)).orElseThrow(() -> concurrentDuplicate);
    }
    try {
      if (purchases != null) purchases.reserve(id, userId, sessionId, quantity, info.purchaseLimit(), 900);
      List<Long> numbers = inventory.reserveQuantity(id, userId, sessionId, quantity);
      order.setTicketNumbers(numbers.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
      orders.updateById(order);
      TicketOrderEntity created = orders.selectById(id);
      outbox.write(EventTypes.ORDER_CREATED, id, new OrderCreatedPayload(created.getId(), created.getUserId(), created.getSessionId(), created.getSeatIds(), created.getStatus(), created.getAmountMinor(), created.getCreatedAt()));
      return created;
    } catch (RuntimeException failure) {
      removeTimeout(id);
      inventory.releaseQuantity(id); if (purchases != null) purchases.release(id, userId, sessionId, quantity); if (promotions != null) promotions.release(coupon.reservationId()); throw failure;
    }
  }

  @Transactional
  public TicketOrderEntity create(String userId, String sessionId, String seatIds, String idempotencyKey) {
    return create(userId, sessionId, seatIds, idempotencyKey, null);
  }

  @Transactional
  public TicketOrderEntity create(String userId, String sessionId, String seatIds, String idempotencyKey, String couponId) {
    requireText(userId, "userId");
    requireText(sessionId, "sessionId");
    requireText(idempotencyKey, "idempotencyKey");
    String key = idempotencyKey.trim();
    List<String> requestedSeats = SeatIds.parse(seatIds);
    String requestHash = requestHash(userId, sessionId, requestedSeats);

    String id = UUID.randomUUID().toString();
    OrderIdempotencyGate.Claim claim = claimIdempotency(key, requestHash, id);
    if (claim.available() && !claim.owner()) {
      TicketOrderEntity existing = orders.selectById(claim.orderId());
      if (existing == null) existing = findByIdempotencyKey(key).orElse(null);
      if (existing != null) return replay(existing, requestHash);
      throw new IllegalStateException("request is already being processed");
    }

    boolean inventoryReserved = false;
    PromotionClient.Quote coupon = null;
    ActivitySessionClient.SessionInfo info = null;
    try {
      // Redis inventory is the first hot-path gate. Only a request that passes it loads session metadata.
      if (inventory != null) {
        inventoryReserved = true;
        inventory.reserve(id, sessionId, requestedSeats);
      }
      int unitPriceMinor = 0;
      if (sessions != null) {
        info = sessions.session(sessionId);
        requireOnSale(info);
        unitPriceMinor = info.priceMinor();
      }
      int originalAmount = unitPriceMinor * requestedSeats.size();
      coupon = promotions == null ? new PromotionClient.Quote(null, 0, originalAmount)
          : promotions.quote(id, userId, info == null ? "" : info.activityId(), sessionId, originalAmount, couponId);

      TicketOrderEntity order = new TicketOrderEntity();
      order.setId(id);
      order.setUserId(userId);
      order.setSessionId(sessionId);
      order.setActivityId(info == null ? "" : info.activityId()); order.setCouponId(couponId); order.setCouponReservationId(coupon.reservationId());
      order.setSeatIds(SeatIds.join(requestedSeats));
      order.setIdempotencyKey(key);
      order.setRequestHash(requestHash);
      order.setStatus("PENDING");
      order.setExpireAt(expiryDeadline());
      order.setOriginalAmountMinor(originalAmount); order.setDiscountAmountMinor(coupon.discountAmountMinor()); order.setAmountMinor(coupon.payableAmountMinor());
      scheduleTimeout(order);
      orders.insert(order);
      TicketOrderEntity created = orders.selectById(id);
      outbox.write(EventTypes.ORDER_CREATED, id,
          new OrderCreatedPayload(created.getId(), created.getUserId(), created.getSessionId(),
              created.getSeatIds(), created.getStatus(), created.getAmountMinor(), created.getCreatedAt()));
      promoteInventory(created.getId());
      return created;
    } catch (DuplicateKeyException concurrentDuplicate) {
      if (inventoryReserved && inventory != null) inventory.release(id);
      if (coupon != null && promotions != null) promotions.release(coupon.reservationId());
      removeTimeout(id);
      return findByIdempotencyKey(key).map(existing -> replay(existing, requestHash))
          .orElseThrow(() -> concurrentDuplicate);
    } catch (RuntimeException failure) {
      if (inventoryReserved && inventory != null) inventory.release(id);
      if (coupon != null && promotions != null) promotions.release(coupon.reservationId());
      removeTimeout(id);
      if (idempotencyGate != null) idempotencyGate.release(claim, key);
      throw failure;
    }
  }

  private OrderIdempotencyGate.Claim claimIdempotency(String key, String requestHash, String orderId) {
    if (idempotencyGate != null) {
      OrderIdempotencyGate.Claim claim = idempotencyGate.claim(key, requestHash, orderId);
      if (claim.available()) return claim;
    }
    return findByIdempotencyKey(key)
        .map(existing -> new OrderIdempotencyGate.Claim(true, false, existing.getId()))
        .orElse(new OrderIdempotencyGate.Claim(true, true, orderId));
  }

  /** Creates the order after Inventory has durably accepted a queued reservation. */
  @Transactional
  public TicketOrderEntity createQueuedHeld(String reservationId, String userId, String sessionId,
                                            String seatIds, int quantity, String ticketNumbers) {
    requireText(reservationId, "reservationId"); requireText(userId, "userId"); requireText(sessionId, "sessionId");
    String idempotencyKey = "queued:" + reservationId;
    Optional<TicketOrderEntity> previous = findByIdempotencyKey(idempotencyKey);
    if (previous.isPresent()) return previous.get();
    ActivitySessionClient.SessionInfo info = sessions.session(sessionId);
    requireOnSale(info);
    TicketOrderEntity order = new TicketOrderEntity();
    order.setId(reservationId); order.setUserId(userId); order.setSessionId(sessionId);
    order.setSeatIds(seatIds == null ? "" : seatIds); order.setQuantity(quantity);
    order.setTicketNumbers(ticketNumbers == null ? "" : ticketNumbers); order.setIdempotencyKey(idempotencyKey);
    List<String> normalizedSeats = seatIds == null || seatIds.isBlank() ? List.of() : SeatIds.parse(seatIds);
    order.setRequestHash(requestHash(userId, sessionId, quantity > 0 ? List.of("QTY:" + quantity) : normalizedSeats));
    order.setStatus("PENDING"); order.setAmountMinor(info.priceMinor() * Math.max(quantity, normalizedSeats.size()));
    order.setExpireAt(expiryDeadline());
    scheduleTimeout(order);
    try {
      orders.insert(order);
    } catch (DuplicateKeyException duplicate) {
      removeTimeout(reservationId);
      return findByIdempotencyKey(idempotencyKey).orElseThrow(() -> duplicate);
    }
    TicketOrderEntity created = orders.selectById(reservationId);
    outbox.write(EventTypes.ORDER_CREATED, reservationId,
        new OrderCreatedPayload(created.getId(), created.getUserId(), created.getSessionId(), created.getSeatIds(),
            created.getStatus(), created.getAmountMinor(), created.getCreatedAt()));
    return created;
  }

  private void scheduleTimeout(TicketOrderEntity order) {
    if (timeoutZset != null) timeoutZset.schedule(order.getId(), order.getExpireAt());
    else if (timeoutOutbox != null) timeoutOutbox.write(order.getId(), order.getExpireAt());
  }

  private void removeTimeout(String orderId) {
    if (timeoutZset != null) timeoutZset.remove(orderId);
  }

  private void promoteInventory(String orderId) {
    if (inventory == null) return;
    try {
      inventory.promote(orderId);
    } catch (RuntimeException ignored) {
      // ORDER_CREATED is durable; the inventory consumer retries promotion after a crash.
    }
  }

  private static void requireOnSale(ActivitySessionClient.SessionInfo info) {
    if (info == null || !"ONSALE".equalsIgnoreCase(info.status())) {
      throw new IllegalStateException("session is not on sale");
    }
  }

  /** Same idempotency key and same request is a replay; a different request is a conflict. */
  private TicketOrderEntity replay(TicketOrderEntity previous, String requestHash) {
    String storedHash = previous.getRequestHash();
    if (storedHash == null || storedHash.isBlank()) {
      if (previous.getQuantity() != null && previous.getQuantity() > 0) {
        storedHash = requestHash(previous.getUserId(), previous.getSessionId(), previous.getQuantity());
      } else {
        storedHash = requestHash(previous.getUserId(), previous.getSessionId(), SeatIds.parse(previous.getSeatIds()));
      }
    }
    if (!requestHash.equals(storedHash)) throw new IdempotencyConflictException();
    return previous;
  }

  public Optional<TicketOrderEntity> find(String id) {
    return Optional.ofNullable(orders.selectById(id));
  }

  public Optional<TicketOrderEntity> findByIdempotencyKey(String key) {
    return Optional.ofNullable(orders.selectOne(
        Wrappers.<TicketOrderEntity>lambdaQuery().eq(TicketOrderEntity::getIdempotencyKey, key)));
  }

  public PageResult<TicketOrderEntity> pageForUser(String userId, int page, int size) {
    return page(Wrappers.<TicketOrderEntity>lambdaQuery()
        .eq(TicketOrderEntity::getUserId, userId)
        .orderByDesc(TicketOrderEntity::getCreatedAt), page, size);
  }

  /**
   * Administrative listing. Callers limited to session scopes get the filter applied in SQL instead
   * of paging over rows they may not read.
   */
  public PageResult<TicketOrderEntity> pageForAdmin(String status, int page, int size,
                                                    String permissions, String scopes) {
    var wrapper = Wrappers.<TicketOrderEntity>lambdaQuery()
        .eq(status != null && !status.isBlank(), TicketOrderEntity::getStatus, trimmed(status))
        .orderByDesc(TicketOrderEntity::getCreatedAt);
    if (!ResourceScopeRule.contains(permissions, "system:config")) {
      wrapper.apply("(FIND_IN_SET(CONCAT('SESSION:',session_id), {0}) > 0 OR FIND_IN_SET('SESSION:*', {0}) > 0)",
          scopes == null ? "" : scopes.replace(" ", ""));
    }
    return page(wrapper, page, size);
  }

  private PageResult<TicketOrderEntity> page(LambdaQueryWrapper<TicketOrderEntity> wrapper, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<TicketOrderEntity> result = orders.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
    return new PageResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
  }

  @Transactional
  public TicketOrderEntity cancel(String id) {
    TicketOrderEntity pending = orders.selectById(id);
    if (orders.cancelIfCancellable(id) == 0) throw new IllegalStateException("order cannot be cancelled");
    releaseReservation(pending);
    TicketOrderEntity cancelled = orders.selectById(id);
    outbox.write(EventTypes.ORDER_CANCELLED, id, new OrderCancelledPayload(id, cancelled.getStatus()));
    return cancelled;
  }

  /** Conditional transition used by the refund review flow; returns whether this call won. */
  @Transactional
  public boolean markRefunded(String orderId, String refundId, String reviewer) {
    TicketOrderEntity paid = orders.selectById(orderId);
    boolean transitioned = orders.markRefundedIfPaid(orderId) > 0;
    if (transitioned) {
      outbox.write(EventTypes.ORDER_REFUNDED, orderId,
          new OrderRefundedPayload(orderId, refundId, reviewer, "REFUNDED"));
    }
    if (transitioned) { releaseReservation(paid); if (promotions != null) promotions.restore(paid.getCouponReservationId()); }
    return transitioned;
  }

  public void releaseReservation(TicketOrderEntity order) {
    if (order == null) return;
    boolean generalAdmission = order.getQuantity() != null && order.getQuantity() > 0;
    if (generalAdmission) {
      if (inventory != null) {
        if ("PAID".equals(order.getStatus())) inventory.releaseRefunded(order.getId());
        else inventory.releaseQuantity(order.getId());
      }
      if (purchases != null) {
        int quantity = order.getQuantity();
        if ("PAID".equals(order.getStatus())) purchases.decrement(order.getId(), order.getUserId(), order.getSessionId(), quantity);
        else purchases.release(order.getId(), order.getUserId(), order.getSessionId(), quantity);
      }
      if (promotions != null && !"PAID".equals(order.getStatus())) promotions.release(order.getCouponReservationId());
      return;
    }
    if (inventory != null) inventory.release(order.getId());
    if (promotions != null && !"PAID".equals(order.getStatus())) promotions.release(order.getCouponReservationId());
  }

  public void releaseCouponReservation(TicketOrderEntity order) {
    if (order != null && promotions != null && !"PAID".equals(order.getStatus())) promotions.release(order.getCouponReservationId());
  }

  /** Orders that are still pending after the payment window; used by the expiry scanner. */
  public List<TicketOrderEntity> findExpiredCandidates(int paymentWindowMinutes) {
    return orders.selectExpiredCandidates(paymentWindowMinutes);
  }

  public List<TicketOrderEntity> findPendingForTimeoutRebuild(int limit) {
    return orders.selectPendingForTimeoutRebuild(Math.max(1, limit));
  }

  /** Conditional transition: only the scanner that wins the update may publish the event. */
  public boolean expireIfStillPending(String orderId, int paymentWindowMinutes) {
    return orders.expireIfStillPending(orderId, paymentWindowMinutes) > 0;
  }

  /** Performs the durable timeout transition and records its release event in one transaction. */
  @Transactional
  public boolean expireAndWriteEvent(String orderId) {
    TicketOrderEntity candidate = orders.selectById(orderId);
    if (candidate == null || orders.expireIfStillPendingByDeadline(orderId) == 0) return false;
    outbox.write(EventTypes.ORDER_EXPIRED, orderId,
        new com.cloudticket.order.event.OrderExpiredPayload(orderId, candidate.getUserId(),
            candidate.getSessionId(), candidate.getSeatIds()));
    return true;
  }

  private Instant expiryDeadline() {
    return Instant.now().plusSeconds(paymentWindowMinutes * 60L);
  }

  private static String requestHash(String userId, String sessionId, List<String> seats) {
    String input = userId + "|" + sessionId + "|" + SeatIds.join(seats);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) hex.append(String.format("%02x", value));
      return hex.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static String requestHash(String userId, String sessionId, int quantity) {
    return requestHash(userId, sessionId, List.of("QTY:" + quantity));
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
  }
}

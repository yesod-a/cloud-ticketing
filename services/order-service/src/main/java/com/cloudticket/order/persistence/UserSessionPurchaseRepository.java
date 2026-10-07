package com.cloudticket.order.persistence;

import com.cloudticket.order.persistence.mapper.UserSessionPurchaseMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.List;
import com.cloudticket.order.persistence.entity.UserSessionPurchaseReservationEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Repository
public class UserSessionPurchaseRepository {
  private final UserSessionPurchaseMapper purchases;
  public UserSessionPurchaseRepository(UserSessionPurchaseMapper purchases) { this.purchases = purchases; }
  public void reserve(String userId, String sessionId, int quantity, int limit, long ttlSeconds) {
    purchases.ensure(userId, sessionId);
    if (purchases.reserve(userId, sessionId, quantity, limit, ttlSeconds) == 0) throw new PurchaseLimitExceededException();
  }
  @Transactional
  public void reserve(String orderId, String userId, String sessionId, int quantity, int limit, long ttlSeconds) {
    if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
    purchases.ensure(userId, sessionId);
    if (purchases.insertReservation(orderId, userId, sessionId, quantity, ttlSeconds) == 0) {
      throw new IllegalStateException("purchase reservation already exists");
    }
    if (purchases.reserveAggregate(userId, sessionId, quantity, limit, ttlSeconds) == 0) {
      purchases.deleteReservation(orderId);
      throw new PurchaseLimitExceededException();
    }
  }
  public void release(String userId, String sessionId, int quantity) { purchases.release(userId, sessionId, quantity); }
  public void activate(String userId, String sessionId, int quantity) { purchases.activate(userId, sessionId, quantity); }
  public void decrement(String userId, String sessionId, int quantity) { purchases.decrement(userId, sessionId, quantity); }
  public void release(String orderId, String userId, String sessionId, int quantity) {
    if (purchases.findReservation(orderId) != null) release(orderId);
    else release(userId, sessionId, quantity);
  }
  public void activate(String orderId, String userId, String sessionId, int quantity) {
    if (purchases.findReservation(orderId) != null) activate(orderId);
    else activate(userId, sessionId, quantity);
  }
  public void decrement(String orderId, String userId, String sessionId, int quantity) {
    if (purchases.findReservation(orderId) != null) decrement(orderId);
    else decrement(userId, sessionId, quantity);
  }
  @Transactional
  public void release(String orderId) {
    UserSessionPurchaseReservationEntity reservation = purchases.findReservation(orderId);
    if (reservation == null || !"HELD".equals(reservation.getState())) return;
    if (purchases.releaseReservation(orderId) > 0) {
      purchases.decreaseReserved(reservation.getUserId(), reservation.getSessionId(), value(reservation.getQuantity()));
    }
  }
  @Transactional
  public void activate(String orderId) {
    UserSessionPurchaseReservationEntity reservation = purchases.findReservation(orderId);
    if (reservation == null || !"HELD".equals(reservation.getState())) return;
    if (purchases.activateReservation(orderId) > 0) {
      purchases.moveReservedToActive(reservation.getUserId(), reservation.getSessionId(), value(reservation.getQuantity()));
    }
  }
  @Transactional
  public void decrement(String orderId) {
    UserSessionPurchaseReservationEntity reservation = purchases.findReservation(orderId);
    if (reservation == null || !"ACTIVE".equals(reservation.getState())) return;
    if (purchases.releaseActiveReservation(orderId) > 0) {
      purchases.decrement(reservation.getUserId(), reservation.getSessionId(), value(reservation.getQuantity()));
    }
  }
  public void expireReservations() {
    List<UserSessionPurchaseReservationEntity> expired = purchases.expiredReservations();
    if (expired != null) expired.forEach(row -> release(row.getOrderId()));
  }
  private static int value(Integer value) { return value == null ? 0 : value; }
  @ResponseStatus(HttpStatus.CONFLICT)
  public static final class PurchaseLimitExceededException extends RuntimeException {
    public PurchaseLimitExceededException() { super("purchase limit exceeded"); }
  }
}

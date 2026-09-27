package com.cloudticket.activity.service;

import com.cloudticket.activity.client.InventorySeatProvisionClient;
import com.cloudticket.activity.domain.Seat;
import com.cloudticket.activity.domain.SeatLayout;
import com.cloudticket.activity.domain.SeatSnapshot;
import com.cloudticket.activity.domain.Session;
import com.cloudticket.activity.persistence.ActivityRepository;
import com.cloudticket.activity.persistence.SessionRepository;
import com.cloudticket.activity.persistence.SessionSeatRepository;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Session lifecycle, its seat snapshot and the hand-off of those seats to the inventory service. */
@Service
public class SessionService {

  private final SessionRepository sessions;
  private final SessionSeatRepository sessionSeats;
  private final ActivityRepository activities;
  private final InventorySeatProvisionClient inventoryClient;
  private final TransactionTemplate transactions;

  public SessionService(SessionRepository sessions, SessionSeatRepository sessionSeats,
                        ActivityRepository activities, InventorySeatProvisionClient inventoryClient,
                        TransactionTemplate transactions) {
    this.sessions = sessions;
    this.sessionSeats = sessionSeats;
    this.activities = activities;
    this.inventoryClient = inventoryClient;
    this.transactions = transactions;
  }

  public List<Session> listOnSale(String activityId) {
    return sessions.listOnSale(activityId);
  }

  public PageResult<Session> adminSessions(String activityId, String status, int page, int size,
                                           String permissions, String scopes) {
    if (ResourceScopeRule.allows(permissions, scopes, "ACTIVITY", activityId)) {
      return sessions.page(activityId, status, page, size);
    }
    return sessions.pageScoped(activityId, status, page, size, scopes);
  }

  /**
   * Creates the session, snapshots the venue seats and republishes the derived activity status.
   *
   * <p>The database work runs in one transaction, but the inventory call deliberately happens after
   * it commits: a remote call must not hold the row locks, and inventory already treats provisioning
   * as idempotent.
   */
  public Session create(String activityId, String venueId, String startsAt, String endsAt, String status,
                        int priceMinor) {
    Session created = Objects.requireNonNull(transactions.execute(ignored -> {
      Session session = sessions.create(activityId, venueId, Instant.parse(startsAt), Instant.parse(endsAt),
          status, priceMinor);
      sessionSeats.copyFromVenue(session.id(), venueId);
      activities.refreshStatus(activityId);
      return session;
    }));
    provisionInventory(created.id(), activityId);
    return sessions.require(created.id());
  }

  public Session update(String sessionId, String startsAt, String endsAt, String status, int priceMinor) {
    Session updated = sessions.update(sessionId, Instant.parse(startsAt), Instant.parse(endsAt), status, priceMinor);
    activities.refreshStatus(updated.activityId());
    return sessions.require(sessionId);
  }

  public Session publish(String sessionId) {
    return sessions.publish(sessionId);
  }

  public Session offline(String sessionId) {
    return sessions.offline(sessionId);
  }

  @Transactional
  public void delete(String sessionId) {
    String activityId = sessions.requireActivityId(sessionId);
    sessionSeats.deleteBySession(sessionId);
    sessions.delete(sessionId);
    activities.refreshStatus(activityId);
  }

  public Session require(String sessionId) {
    return sessions.require(sessionId);
  }

  public List<SeatLayout> seatLayouts(String sessionId) {
    return sessionSeats.layouts(sessionId);
  }

  public List<Seat> seats(String sessionId) {
    return sessionSeats.list(sessionId);
  }

  public List<SeatSnapshot> snapshots(String sessionId) {
    return sessionSeats.snapshots(sessionId);
  }

  public Seat updateSeat(String seatId, String status) {
    return sessionSeats.updateStatus(seatId, status);
  }

  public Seat requireSeat(String seatId) {
    return sessionSeats.require(seatId);
  }

  public void refreshActivityStatus(String activityId) {
    activities.refreshStatus(activityId);
  }

  private void provisionInventory(String sessionId, String activityId) {
    if (inventoryClient == null) return;
    List<Map<String, Object>> seats = sessionSeats.snapshots(sessionId).stream()
        .map(seat -> Map.<String, Object>of(
            "id", seat.id(), "rowLabel", seat.rowLabel(), "seatNumber", seat.seatNumber(),
            "areaLabel", seat.areaLabel(), "displayName", seat.displayName(), "seatType", seat.seatType(),
            "x", seat.x(), "y", seat.y(), "status", seat.status()))
        .toList();
    inventoryClient.provision(sessionId, activityId, seats);
  }
}

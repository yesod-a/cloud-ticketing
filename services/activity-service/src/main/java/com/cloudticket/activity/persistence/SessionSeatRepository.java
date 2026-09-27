package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.domain.Seat;
import com.cloudticket.activity.domain.SeatLayout;
import com.cloudticket.activity.domain.SeatSnapshot;
import com.cloudticket.activity.persistence.entity.SeatEntity;
import com.cloudticket.activity.persistence.mapper.SeatMapper;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Persistence for the {@code seat} rows that belong to a session. */
@Repository
public class SessionSeatRepository {

  private static final String AVAILABLE = "AVAILABLE";

  private final SeatMapper seats;

  public SessionSeatRepository(SeatMapper seats) {
    this.seats = seats;
  }

  /** Copies the venue template into independent session seats in one statement. */
  public int copyFromVenue(String sessionId, String venueId) {
    return seats.copyFromVenue(sessionId, venueId);
  }

  public void deleteBySession(String sessionId) {
    seats.delete(Wrappers.<SeatEntity>lambdaQuery().eq(SeatEntity::getSessionId, sessionId));
  }

  public List<SeatLayout> layouts(String sessionId) {
    return seats.selectList(Wrappers.<SeatEntity>lambdaQuery()
            .eq(SeatEntity::getSessionId, sessionId)
            .orderByAsc(SeatEntity::getAreaLabel, SeatEntity::getPositionY, SeatEntity::getPositionX,
                SeatEntity::getRowLabel, SeatEntity::getSeatNumber))
        .stream()
        .map(row -> new SeatLayout(row.getId(), row.getAreaLabel(), row.getRowLabel(),
            number(row.getSeatNumber()), row.getDisplayName(), whole(row.getPositionX()),
            whole(row.getPositionY()), row.getSeatType(), row.getStatus()))
        .toList();
  }

  public List<Seat> list(String sessionId) {
    return seats.selectList(Wrappers.<SeatEntity>lambdaQuery()
            .eq(SeatEntity::getSessionId, sessionId)
            .orderByAsc(SeatEntity::getRowLabel, SeatEntity::getSeatNumber))
        .stream().map(SessionSeatRepository::toSeat).toList();
  }

  public List<SeatSnapshot> snapshots(String sessionId) {
    return seats.selectList(Wrappers.<SeatEntity>lambdaQuery()
            .eq(SeatEntity::getSessionId, sessionId)
            .orderByAsc(SeatEntity::getRowLabel, SeatEntity::getSeatNumber))
        .stream()
        .map(row -> new SeatSnapshot(row.getId(), row.getAreaLabel(), row.getRowLabel(),
            number(row.getSeatNumber()), row.getDisplayName(), whole(row.getPositionX()),
            whole(row.getPositionY()), row.getSeatType(), row.getStatus()))
        .toList();
  }

  /**
   * Seats of every session of an activity, narrowed to what the caller may see.
   *
   * <p>A seat is visible when the caller holds a scope for its session or for the owning activity.
   */
  public PageResult<Seat> pageForActivity(String activityId, String permissions, String scopes,
                                          int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    if (ResourceScopeRule.contains(permissions, "system:config")) {
      List<Seat> all = seats.selectByActivity(activityId).stream().map(SessionSeatRepository::toSeat).toList();
      return PageResult.slice(all, safePage, safeSize);
    }
    Page<SeatEntity> result = seats.selectScopedByActivity(new Page<>(safePage + 1L, safeSize),
        activityId, Text.orEmpty(scopes).replace(" ", ""));
    return new PageResult<>(result.getRecords().stream().map(SessionSeatRepository::toSeat).toList(),
        safePage, safeSize, result.getTotal());
  }

  public Seat updateStatus(String id, String status) {
    seats.update(null, Wrappers.<SeatEntity>lambdaUpdate()
        .set(SeatEntity::getStatus, status)
        .eq(SeatEntity::getId, id));
    return require(id);
  }

  public Optional<Seat> find(String id) {
    return Optional.ofNullable(seats.selectById(id)).map(SessionSeatRepository::toSeat);
  }

  public Seat require(String id) {
    return find(id).orElseThrow(() -> new NoSuchElementException("seat not found"));
  }

  public String activityIdForSeat(String seatId) {
    return seats.selectActivityIdForSeat(seatId).stream().findFirst().orElse(null);
  }

  private static Seat toSeat(SeatEntity row) {
    return new Seat(row.getId(), row.getRowLabel(), number(row.getSeatNumber()),
        row.getStatus() == null ? AVAILABLE : row.getStatus());
  }

  private static int number(Integer value) {
    return value == null ? 0 : value;
  }

  private static int whole(BigDecimal value) {
    return value == null ? 0 : value.intValue();
  }
}

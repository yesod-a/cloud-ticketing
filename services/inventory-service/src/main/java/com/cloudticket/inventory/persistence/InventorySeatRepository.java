package com.cloudticket.inventory.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.common.web.PageResult;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.cloudticket.inventory.persistence.mapper.InventorySeatMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@code inventory_seat}.
 *
 * <p>Seat state changes are conditional single statements — the predicate names the status the row
 * must still be in — so two concurrent requests can never both win. Modelling these transitions as
 * "read, decide, write" would break that guarantee.
 */
@Repository
public class InventorySeatRepository {

  public static final String AVAILABLE = "AVAILABLE";
  public static final String LOCKED = "LOCKED";
  public static final String SOLD = "SOLD";
  public static final String DISABLED = "DISABLED";
  public static final List<String> STATUSES = List.of(AVAILABLE, LOCKED, SOLD, DISABLED);

  private final InventorySeatMapper seats;

  public InventorySeatRepository(InventorySeatMapper seats) {
    this.seats = seats;
  }

  /** @return how many of the requested seats were still available and are now locked */
  public int lock(String sessionId, List<String> seatIds) {
    return seats.lockBatch(sessionId, seatIds);
  }

  /** @return how many locked seats were returned to the pool */
  public int release(List<String> seatIds) {
    if (seatIds.isEmpty()) return 0;
    return seats.update(null, Wrappers.<InventorySeatEntity>lambdaUpdate()
        .set(InventorySeatEntity::getStatus, AVAILABLE)
        .in(InventorySeatEntity::getId, seatIds)
        .eq(InventorySeatEntity::getStatus, LOCKED));
  }

  /** @return how many locked seats became permanently sold */
  public int sell(List<String> seatIds) {
    if (seatIds.isEmpty()) return 0;
    return seats.update(null, Wrappers.<InventorySeatEntity>lambdaUpdate()
        .set(InventorySeatEntity::getStatus, SOLD)
        .in(InventorySeatEntity::getId, seatIds)
        .eq(InventorySeatEntity::getStatus, LOCKED));
  }

  public int adjust(String seatId, String status) {
    return seats.update(null, Wrappers.<InventorySeatEntity>lambdaUpdate()
        .set(InventorySeatEntity::getStatus, status)
        .eq(InventorySeatEntity::getId, seatId));
  }

  public Optional<InventorySeatEntity> find(String id) {
    return Optional.ofNullable(seats.selectById(id));
  }

  /** Seat map of one session in the order the public picker renders it. */
  public List<InventorySeatEntity> listBySession(String sessionId) {
    return seats.selectList(Wrappers.<InventorySeatEntity>lambdaQuery()
        .eq(InventorySeatEntity::getSessionId, sessionId)
        .orderByAsc(InventorySeatEntity::getSeatIndex, InventorySeatEntity::getAreaLabel, InventorySeatEntity::getPositionY,
            InventorySeatEntity::getPositionX, InventorySeatEntity::getRowLabel,
            InventorySeatEntity::getSeatNumber));
  }

  public Map<String, Integer> indexes(String sessionId, List<String> seatIds) {
    if (seatIds == null || seatIds.isEmpty()) return Map.of();
    return seats.selectList(Wrappers.<InventorySeatEntity>lambdaQuery()
            .select(InventorySeatEntity::getId, InventorySeatEntity::getSeatIndex)
            .eq(InventorySeatEntity::getSessionId, sessionId)
            .in(InventorySeatEntity::getId, seatIds))
        .stream()
        .filter(seat -> seat.getSeatIndex() != null)
        .collect(Collectors.toMap(InventorySeatEntity::getId, InventorySeatEntity::getSeatIndex));
  }

  public List<String> idsByIndexes(String sessionId, List<Integer> indexes) {
    if (indexes == null || indexes.isEmpty()) return List.of();
    return seats.idsByIndexes(sessionId, indexes);
  }

  public List<String> sessionIds() { return seats.selectSessionIds(); }

  public PageResult<InventorySeatEntity> page(String sessionId, String status, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<InventorySeatEntity> result = seats.selectPage(new Page<>(safePage + 1L, safeSize),
        filter(sessionId, status));
    return new PageResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
  }

  public PageResult<InventorySeatEntity> pageScoped(String sessionId, String status, String scopes,
                                                   int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<InventorySeatEntity> result = seats.selectScopedPage(new Page<>(safePage + 1L, safeSize),
        Text.orEmpty(sessionId).trim(), Text.orEmpty(status).trim(), Text.orEmpty(scopes).replace(" ", ""));
    return new PageResult<>(result.getRecords(), safePage, safeSize, result.getTotal());
  }

  /** Replaces the whole seat set of a session with the layout pushed by activity-service. */
  @org.springframework.transaction.annotation.Transactional
  public void replaceSessionSeats(String sessionId, List<InventorySeatEntity> replacement) {
    List<InventorySeatEntity> existing = listBySession(sessionId);
    SeatIndexAllocator.assign(existing, replacement);
    seats.delete(Wrappers.<InventorySeatEntity>lambdaQuery().eq(InventorySeatEntity::getSessionId, sessionId));
    for (InventorySeatEntity seat : replacement) {
      seats.insert(seat);
    }
  }

  private static LambdaQueryWrapper<InventorySeatEntity> filter(String sessionId, String status) {
    String cleanSession = Text.orEmpty(sessionId).trim();
    String cleanStatus = Text.orEmpty(status).trim();
    return Wrappers.<InventorySeatEntity>lambdaQuery()
        .eq(!cleanSession.isBlank(), InventorySeatEntity::getSessionId, cleanSession)
        .eq(!cleanStatus.isBlank(), InventorySeatEntity::getStatus, cleanStatus)
        .orderByAsc(InventorySeatEntity::getSessionId, InventorySeatEntity::getRowLabel,
            InventorySeatEntity::getSeatNumber);
  }

  public static InventorySeatEntity seat(String id, String sessionId, String activityId, String areaLabel,
                                         String rowLabel, int seatNumber, String displayName, String seatType,
                                         BigDecimal x, BigDecimal y, String status) {
    InventorySeatEntity seat = new InventorySeatEntity();
    seat.setId(id);
    seat.setSessionId(sessionId);
    seat.setActivityId(Text.trimmedOrNull(activityId));
    seat.setAreaLabel(Text.orEmpty(areaLabel));
    seat.setRowLabel(Text.orEmpty(rowLabel));
    seat.setSeatNumber(seatNumber);
    seat.setDisplayName(Text.orEmpty(displayName));
    seat.setSeatType(Text.orEmpty(seatType));
    seat.setPositionX(x);
    seat.setPositionY(y);
    seat.setStatus(status);
    return seat;
  }
}

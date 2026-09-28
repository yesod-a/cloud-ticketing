package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.domain.Session;
import com.cloudticket.activity.persistence.entity.SessionEntity;
import com.cloudticket.activity.persistence.entity.VenueEntity;
import com.cloudticket.activity.persistence.mapper.SessionMapper;
import com.cloudticket.activity.persistence.mapper.VenueMapper;
import com.cloudticket.common.web.PageResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence and lifecycle transitions for {@code activity_session}. */
@Repository
public class SessionRepository {

  private static final String DRAFT = "DRAFT";

  private final SessionMapper sessions;
  private final VenueMapper venues;

  public SessionRepository(SessionMapper sessions, VenueMapper venues) {
    this.sessions = sessions;
    this.venues = venues;
  }

  public List<Session> listOnSale(String activityId) {
    return views(sessions.selectOnSale(activityId));
  }

  public PageResult<Session> page(String activityId, String status, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<SessionEntity> result = sessions.selectPage(new Page<>(safePage + 1L, safeSize), filter(activityId, status));
    return new PageResult<>(views(result.getRecords()), safePage, safeSize, result.getTotal());
  }

  /** Listing for an operator limited to session or venue scopes; the filter runs in SQL. */
  public PageResult<Session> pageScoped(String activityId, String status, int page, int size, String scopes) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<SessionEntity> result = sessions.selectScopedPage(new Page<>(safePage + 1L, safeSize), activityId,
        Text.orEmpty(status).trim(), Text.orEmpty(scopes).replace(" ", ""));
    return new PageResult<>(views(result.getRecords()), safePage, safeSize, result.getTotal());
  }

  public long countForVenue(String venueId) {
    return sessions.selectCount(Wrappers.<SessionEntity>lambdaQuery().eq(SessionEntity::getVenueId, venueId));
  }

  public Session create(String activityId, String venueId, Instant startsAt, Instant endsAt, String status,
                        int priceMinor) {
    return create(activityId, venueId, startsAt, endsAt, status, priceMinor, "GRID", 0, 0);
  }

  public Session create(String activityId, String venueId, Instant startsAt, Instant endsAt, String status,
                        int priceMinor, String layoutMode, int capacity, int purchaseLimit) {
    requireTimeWindow(startsAt, endsAt);
    requirePrice(priceMinor);
    String mode = requireMode(layoutMode);
    requireCapacity(mode, capacity);
    requirePurchaseLimit(purchaseLimit);
    SessionEntity entity = new SessionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setActivityId(activityId);
    entity.setVenueId(venueId);
    entity.setStartsAt(startsAt);
    entity.setEndsAt(endsAt);
    entity.setStatus(status == null || status.isBlank() ? DRAFT : status);
    entity.setPriceMinor(priceMinor);
    entity.setLayoutMode(mode);
    entity.setCapacity(capacity);
    entity.setPurchaseLimit(purchaseLimit);
    sessions.insert(entity);
    return require(entity.getId());
  }

  public Session update(String sessionId, Instant startsAt, Instant endsAt, String status, int priceMinor) {
    Session current = require(sessionId);
    return update(sessionId, startsAt, endsAt, status, priceMinor, current.layoutMode(), current.capacity(),
        current.purchaseLimit());
  }

  public Session update(String sessionId, Instant startsAt, Instant endsAt, String status, int priceMinor,
                        String layoutMode, int capacity, int purchaseLimit) {
    requireTimeWindow(startsAt, endsAt);
    requirePrice(priceMinor);
    String mode = requireMode(layoutMode);
    requireCapacity(mode, capacity);
    requirePurchaseLimit(purchaseLimit);
    Session current = require(sessionId);
    if (!"DRAFT".equalsIgnoreCase(current.status())
        && (!mode.equalsIgnoreCase(current.layoutMode()) || capacity != current.capacity())) {
      throw new IllegalStateException("layout mode and capacity are immutable after publishing");
    }
    sessions.update(null, Wrappers.<SessionEntity>lambdaUpdate()
        .set(SessionEntity::getStartsAt, startsAt)
        .set(SessionEntity::getEndsAt, endsAt)
        .set(SessionEntity::getStatus, status == null || status.isBlank() ? DRAFT : status)
        .set(SessionEntity::getPriceMinor, priceMinor)
        .set(SessionEntity::getLayoutMode, mode)
        .set(SessionEntity::getCapacity, capacity)
        .set(SessionEntity::getPurchaseLimit, purchaseLimit)
        .eq(SessionEntity::getId, sessionId));
    return require(sessionId);
  }

  public Session publish(String sessionId) {
    return transition(sessionId, "ONSALE");
  }

  public Session offline(String sessionId) {
    return transition(sessionId, "OFFLINE");
  }

  public void delete(String sessionId) {
    sessions.deleteById(sessionId);
  }

  public Optional<SessionEntity> find(String sessionId) {
    return Optional.ofNullable(sessions.selectById(sessionId));
  }

  public Session require(String sessionId) {
    SessionEntity entity = find(sessionId).orElseThrow(() -> new NoSuchElementException("session not found"));
    return view(entity, venueNames(List.of(entity)));
  }

  public String requireActivityId(String sessionId) {
    return find(sessionId)
        .map(SessionEntity::getActivityId)
        .orElseThrow(() -> new NoSuchElementException("session not found"));
  }

  public String activityIdOf(String sessionId) {
    return find(sessionId).map(SessionEntity::getActivityId).orElse(null);
  }

  public long countForActivity(String activityId) {
    return sessions.selectCount(Wrappers.<SessionEntity>lambdaQuery()
        .eq(SessionEntity::getActivityId, activityId));
  }

  private Session transition(String sessionId, String status) {
    sessions.update(null, Wrappers.<SessionEntity>lambdaUpdate()
        .set(SessionEntity::getStatus, status)
        .eq(SessionEntity::getId, sessionId));
    return require(sessionId);
  }

  private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity> filter(
      String activityId, String status) {
    String cleanStatus = Text.orEmpty(status).trim();
    return Wrappers.<SessionEntity>lambdaQuery()
        .eq(SessionEntity::getActivityId, activityId)
        .eq(!cleanStatus.isBlank(), SessionEntity::getStatus, cleanStatus)
        .orderByAsc(SessionEntity::getStartsAt);
  }

  /**
   * Venue names are resolved with one extra query instead of a join, so the session row stays a plain
   * entity mapping.
   */
  private List<Session> views(List<SessionEntity> rows) {
    Map<String, String> names = venueNames(rows);
    return rows.stream().map(row -> view(row, names)).toList();
  }

  private Session view(SessionEntity row, Map<String, String> venueNames) {
    return Session.from(row.getId(), row.getActivityId(), row.getStartsAt(), row.getEndsAt(),
        venueNames.getOrDefault(row.getVenueId(), ""), row.getStatus(),
        row.getPriceMinor() == null ? 0 : row.getPriceMinor(),
        row.getLayoutMode() == null || row.getLayoutMode().isBlank() ? "GRID" : row.getLayoutMode(),
        row.getCapacity() == null ? 0 : row.getCapacity(),
        row.getPurchaseLimit() == null ? 0 : row.getPurchaseLimit());
  }

  private Map<String, String> venueNames(List<SessionEntity> rows) {
    List<String> venueIds = rows.stream().map(SessionEntity::getVenueId).filter(java.util.Objects::nonNull)
        .distinct().toList();
    if (venueIds.isEmpty()) return Map.of();
    Map<String, String> names = new HashMap<>();
    venues.selectBatchIds(venueIds).forEach(venue -> names.put(venue.getId(), venue.getName()));
    return names;
  }

  private static void requireTimeWindow(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null) throw new IllegalArgumentException("start and end required");
    if (!startsAt.isBefore(endsAt)) throw new IllegalArgumentException("startsAt must be before endsAt");
  }

  private static void requirePrice(int priceMinor) {
    if (priceMinor < 0) throw new IllegalArgumentException("price must be non-negative");
  }

  private static String requireMode(String mode) {
    String clean = mode == null || mode.isBlank() ? "GRID" : mode.trim().toUpperCase(java.util.Locale.ROOT);
    if (!java.util.Set.of("GRID", "ROWS", "GENERAL_ADMISSION").contains(clean)) {
      throw new IllegalArgumentException("layoutMode must be GRID, ROWS or GENERAL_ADMISSION");
    }
    return clean;
  }

  private static void requireCapacity(String mode, int capacity) {
    if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
    if ("GENERAL_ADMISSION".equals(mode) && capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive for GENERAL_ADMISSION");
    }
  }

  private static void requirePurchaseLimit(int purchaseLimit) {
    if (purchaseLimit < 0) throw new IllegalArgumentException("purchaseLimit must be non-negative");
  }
}

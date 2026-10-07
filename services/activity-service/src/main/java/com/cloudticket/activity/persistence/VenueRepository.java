package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.domain.Venue;
import com.cloudticket.activity.persistence.entity.VenueEntity;
import com.cloudticket.activity.persistence.entity.VenueSeatEntity;
import com.cloudticket.activity.persistence.mapper.VenueMapper;
import com.cloudticket.activity.persistence.mapper.VenueSeatMapper;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence for {@code venue}; venues may outlive the activity they were created under. */
@Repository
public class VenueRepository {

  private final VenueMapper venues;
  private final VenueSeatMapper venueSeats;

  public VenueRepository(VenueMapper venues, VenueSeatMapper venueSeats) {
    this.venues = venues;
    this.venueSeats = venueSeats;
  }

  public Venue create(String activityId, String name, String address, int capacity) {
    String cleanName = requireName(name);
    if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
    VenueEntity entity = new VenueEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setActivityId(Text.trimmedOrNull(activityId));
    entity.setName(cleanName);
    entity.setAddress(Text.orEmpty(address).trim());
    entity.setCapacity(capacity);
    venues.insert(entity);
    return toDomain(entity);
  }

  public Venue update(String id, String name, String address) {
    String cleanName = requireName(name);
    venues.update(null, Wrappers.<VenueEntity>lambdaUpdate()
        .set(VenueEntity::getName, cleanName)
        .set(VenueEntity::getAddress, Text.orEmpty(address).trim())
        .eq(VenueEntity::getId, id));
    return require(id);
  }

  public List<Venue> list(String activityId) {
    String cleanActivityId = Text.orEmpty(activityId).trim();
    return venues.selectList(Wrappers.<VenueEntity>lambdaQuery()
            .eq(!cleanActivityId.isBlank(), VenueEntity::getActivityId, cleanActivityId)
            .orderByAsc(VenueEntity::getName))
        .stream().map(VenueRepository::toDomain).toList();
  }

  public PageResult<Venue> page(String keyword, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    String cleanKeyword = Text.orEmpty(keyword).trim();
    Page<VenueEntity> result = venues.selectPage(new Page<>(safePage + 1L, safeSize),
        Wrappers.<VenueEntity>lambdaQuery()
            .like(!cleanKeyword.isBlank(), VenueEntity::getName, cleanKeyword)
            .orderByAsc(VenueEntity::getName));
    return new PageResult<>(result.getRecords().stream().map(VenueRepository::toDomain).toList(),
        safePage, safeSize, result.getTotal());
  }

  public Optional<Venue> find(String id) {
    return Optional.ofNullable(venues.selectById(id)).map(VenueRepository::toDomain);
  }

  /** Detaches venues from an activity while preserving the reusable venue and seat template. */
  public void detachFromActivity(String activityId) {
    venues.update(null, Wrappers.<VenueEntity>lambdaUpdate()
        .set(VenueEntity::getActivityId, null)
        .eq(VenueEntity::getActivityId, activityId));
  }

  public Venue require(String id) {
    return find(id).orElseThrow(() -> new NoSuchElementException("venue not found"));
  }

  /** Removes the venue together with its seat template. */
  public void delete(String id) {
    venueSeats.delete(Wrappers.<VenueSeatEntity>lambdaQuery().eq(VenueSeatEntity::getVenueId, id));
    venues.deleteById(id);
  }

  public void refreshCapacity(String venueId) {
    venues.refreshCapacity(venueId);
  }

  public void setCapacity(String venueId, int capacity) {
    if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
    venues.update(null, Wrappers.<VenueEntity>lambdaUpdate()
        .set(VenueEntity::getCapacity, capacity)
        .eq(VenueEntity::getId, venueId));
  }

  private static Venue toDomain(VenueEntity entity) {
    return new Venue(entity.getId(), entity.getActivityId(), entity.getName(), entity.getAddress(),
        entity.getCapacity() == null ? 0 : entity.getCapacity());
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
    return name.trim();
  }
}

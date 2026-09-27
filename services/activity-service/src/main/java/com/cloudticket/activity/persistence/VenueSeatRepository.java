package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.activity.domain.VenueSeat;
import com.cloudticket.activity.persistence.entity.VenueSeatEntity;
import com.cloudticket.activity.persistence.mapper.VenueSeatMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence for the {@code venue_seat} template. */
@Repository
public class VenueSeatRepository {

  private static final String DEFAULT_SEAT_TYPE = "REGULAR";
  private static final String DEFAULT_STATUS = "AVAILABLE";

  private final VenueSeatMapper venueSeats;
  private final VenueRepository venues;

  public VenueSeatRepository(VenueSeatMapper venueSeats, VenueRepository venues) {
    this.venueSeats = venueSeats;
    this.venues = venues;
  }

  public List<VenueSeat> list(String venueId) {
    return venueSeats.selectList(Wrappers.<VenueSeatEntity>lambdaQuery()
            .eq(VenueSeatEntity::getVenueId, venueId)
            .orderByAsc(VenueSeatEntity::getAreaLabel, VenueSeatEntity::getRowLabel, VenueSeatEntity::getSeatNumber))
        .stream().map(VenueSeatRepository::toDomain).toList();
  }

  @Transactional
  public VenueSeat create(String venueId, String areaLabel, String rowLabel, int seatNumber, String displayName,
                          int x, int y, String seatType, boolean enabled) {
    if (venueId == null || venueId.isBlank() || rowLabel == null || rowLabel.isBlank() || seatNumber <= 0) {
      throw new IllegalArgumentException("venueId, rowLabel and positive seatNumber required");
    }
    String row = rowLabel.trim();
    String display = displayName == null || displayName.isBlank() ? row + "-" + seatNumber : displayName.trim();
    VenueSeatEntity entity = new VenueSeatEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setVenueId(venueId.trim());
    entity.setAreaLabel(Text.orEmpty(areaLabel).trim());
    entity.setRowLabel(row);
    entity.setSeatNumber(seatNumber);
    entity.setDisplayName(display);
    entity.setPositionX(BigDecimal.valueOf(x));
    entity.setPositionY(BigDecimal.valueOf(y));
    entity.setSeatType(seatType == null || seatType.isBlank() ? DEFAULT_SEAT_TYPE : seatType.trim());
    entity.setEnabled(enabled);
    entity.setStatus(DEFAULT_STATUS);
    venueSeats.insert(entity);
    venues.refreshCapacity(entity.getVenueId());
    return toDomain(entity);
  }

  public VenueSeat update(String id, String areaLabel, String rowLabel, int seatNumber, String displayName,
                          int x, int y, String seatType, boolean enabled, String status) {
    if (rowLabel == null || rowLabel.isBlank() || seatNumber <= 0) throw new IllegalArgumentException("invalid seat");
    venueSeats.update(null, Wrappers.<VenueSeatEntity>lambdaUpdate()
        .set(VenueSeatEntity::getAreaLabel, Text.orEmpty(areaLabel).trim())
        .set(VenueSeatEntity::getRowLabel, rowLabel.trim())
        .set(VenueSeatEntity::getSeatNumber, seatNumber)
        .set(VenueSeatEntity::getDisplayName, Text.orEmpty(displayName).trim())
        .set(VenueSeatEntity::getPositionX, BigDecimal.valueOf(x))
        .set(VenueSeatEntity::getPositionY, BigDecimal.valueOf(y))
        .set(VenueSeatEntity::getSeatType,
            seatType == null || seatType.isBlank() ? DEFAULT_SEAT_TYPE : seatType.trim())
        .set(VenueSeatEntity::getEnabled, enabled)
        .set(VenueSeatEntity::getStatus, status == null || status.isBlank() ? DEFAULT_STATUS : status.trim())
        .eq(VenueSeatEntity::getId, id));
    return find(id).orElseThrow(() -> new NoSuchElementException("venue seat not found"));
  }

  public Optional<VenueSeat> find(String id) {
    return Optional.ofNullable(venueSeats.selectById(id)).map(VenueSeatRepository::toDomain);
  }

  /** Replaces the whole template in one transaction and keeps the venue capacity in step. */
  @Transactional
  public List<VenueSeat> replaceAll(String venueId, List<VenueSeat> generated) {
    venueSeats.delete(Wrappers.<VenueSeatEntity>lambdaQuery().eq(VenueSeatEntity::getVenueId, venueId));
    for (VenueSeat seat : generated) {
      VenueSeatEntity entity = new VenueSeatEntity();
      entity.setId(seat.id());
      entity.setVenueId(seat.venueId());
      entity.setAreaLabel(seat.areaLabel());
      entity.setRowLabel(seat.rowLabel());
      entity.setSeatNumber(seat.seatNumber());
      entity.setDisplayName(seat.displayName());
      entity.setPositionX(BigDecimal.valueOf(seat.x()));
      entity.setPositionY(BigDecimal.valueOf(seat.y()));
      entity.setSeatType(seat.seatType());
      entity.setEnabled(seat.enabled());
      entity.setStatus(seat.status());
      venueSeats.insert(entity);
    }
    venues.refreshCapacity(venueId);
    return generated;
  }

  public void delete(String id) {
    String venueId = Optional.ofNullable(venueSeats.selectById(id))
        .map(VenueSeatEntity::getVenueId)
        .orElse(null);
    venueSeats.deleteById(id);
    if (venueId != null) venues.refreshCapacity(venueId);
  }

  private static VenueSeat toDomain(VenueSeatEntity entity) {
    return new VenueSeat(entity.getId(), entity.getVenueId(), entity.getAreaLabel(), entity.getRowLabel(),
        entity.getSeatNumber() == null ? 0 : entity.getSeatNumber(), entity.getDisplayName(),
        wholeNumber(entity.getPositionX()), wholeNumber(entity.getPositionY()),
        entity.getSeatType(), Boolean.TRUE.equals(entity.getEnabled()), entity.getStatus());
  }

  private static int wholeNumber(BigDecimal value) {
    return value == null ? 0 : value.intValue();
  }
}

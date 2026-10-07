package com.cloudticket.activity.service;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.Venue;
import com.cloudticket.activity.domain.VenueSeat;
import com.cloudticket.activity.layout.GeneralAdmissionLayoutStrategy;
import com.cloudticket.activity.layout.SeatLayoutStrategy;
import com.cloudticket.activity.layout.SeatLayoutStrategyRegistry;
import com.cloudticket.activity.persistence.SessionRepository;
import com.cloudticket.activity.persistence.VenueRepository;
import com.cloudticket.activity.persistence.VenueSeatRepository;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Venue administration plus the seat template that sessions are snapshotted from. */
@Service
public class VenueService {

  private final VenueRepository venues;
  private final VenueSeatRepository venueSeats;
  private final SessionRepository sessions;
  private final SeatLayoutStrategyRegistry layouts;

  public VenueService(VenueRepository venues, VenueSeatRepository venueSeats, SessionRepository sessions,
                      SeatLayoutStrategyRegistry layouts) {
    this.venues = venues;
    this.venueSeats = venueSeats;
    this.sessions = sessions;
    this.layouts = layouts;
  }

  public List<Venue> list(String activityId) {
    return venues.list(activityId);
  }

  public List<Venue> list(String activityId, String permissions, String scopes) {
    return venues.list(activityId).stream()
        .filter(venue -> ResourceScopeRule.allows(permissions, scopes, "VENUE", venue.id(), venue.activityId()))
        .toList();
  }

  public PageResult<Venue> page(String keyword, int page, int size) {
    return venues.page(keyword, page, size);
  }

  public Venue create(String activityId, String name, String address) {
    return venues.create(activityId, name, address, 0);
  }

  public Venue update(String id, String name, String address) {
    return venues.update(id, name, address);
  }

  public Venue require(String id) {
    return venues.require(id);
  }

  public Optional<Venue> find(String id) {
    return venues.find(id);
  }

  public void delete(String id) {
    if (sessions.countForVenue(id) > 0) {
      throw new IllegalStateException("venue has sessions and cannot be deleted");
    }
    venues.delete(id);
  }

  public List<VenueSeat> seats(String venueId) {
    return venueSeats.list(venueId);
  }

  public VenueSeat createSeat(String venueId, String areaLabel, String rowLabel, int seatNumber,
                              String displayName, int x, int y, String seatType, boolean enabled) {
    return venueSeats.create(venueId, areaLabel, rowLabel, seatNumber, displayName, x, y, seatType, enabled);
  }

  public VenueSeat updateSeat(String id, String areaLabel, String rowLabel, int seatNumber, String displayName,
                              int x, int y, String seatType, boolean enabled, String status) {
    return venueSeats.update(id, areaLabel, rowLabel, seatNumber, displayName, x, y, seatType, enabled, status);
  }

  public void deleteSeat(String id) {
    venueSeats.delete(id);
  }

  /**
   * Regenerates the whole template for a venue through the strategy registered for the requested
   * mode, replacing the stored layout in one transaction.
   */
  public List<VenueSeat> generateLayout(String venueId, SeatLayoutRules rules) {
    if (venueId == null || venueId.isBlank()) throw new IllegalArgumentException("venueId required");
    SeatLayoutStrategy strategy = layouts.resolve(rules.mode());
    List<VenueSeat> generated = strategy.generate(venueId, rules);
    return GeneralAdmissionLayoutStrategy.MODE.equalsIgnoreCase(rules.mode())
        ? venueSeats.replaceAll(venueId, generated, rules.capacityOrZero())
        : venueSeats.replaceAll(venueId, generated);
  }
}

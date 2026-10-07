package com.cloudticket.activity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.VenueSeat;
import com.cloudticket.activity.layout.GeneralAdmissionLayoutStrategy;
import com.cloudticket.activity.layout.SeatLayoutStrategy;
import com.cloudticket.activity.layout.SeatLayoutStrategyRegistry;
import com.cloudticket.activity.persistence.SessionRepository;
import com.cloudticket.activity.persistence.VenueRepository;
import com.cloudticket.activity.persistence.VenueSeatRepository;
import com.cloudticket.activity.service.VenueService;
import java.util.List;
import org.junit.jupiter.api.Test;

class VenueServiceTest {

  @Test
  void generalAdmissionPersistsCapacityEvenThoughItHasNoPhysicalSeats() {
    VenueRepository venues = mock(VenueRepository.class);
    VenueSeatRepository venueSeats = mock(VenueSeatRepository.class);
    SessionRepository sessions = mock(SessionRepository.class);
    SeatLayoutStrategy strategy = new GeneralAdmissionLayoutStrategy();
    SeatLayoutStrategyRegistry layouts = new SeatLayoutStrategyRegistry(List.of(strategy));
    VenueService service = new VenueService(venues, venueSeats, sessions, layouts);

    SeatLayoutRules rules = new SeatLayoutRules("GENERAL_ADMISSION", "", null, null, null, null, List.of(), 25);
    when(venueSeats.replaceAll("venue-1", List.<VenueSeat>of(), 25)).thenReturn(List.of());

    service.generateLayout("venue-1", rules);

    verify(venueSeats).replaceAll("venue-1", List.<VenueSeat>of(), 25);
  }
}

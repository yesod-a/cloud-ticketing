package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.VenueSeat;
import com.cloudticket.activity.layout.GridSeatLayoutStrategy;
import com.cloudticket.activity.layout.GeneralAdmissionLayoutStrategy;
import com.cloudticket.activity.layout.RowSeatLayoutStrategy;
import com.cloudticket.activity.layout.SeatLayoutStrategyRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatLayoutStrategyTest {

  private final SeatLayoutStrategyRegistry layouts = new SeatLayoutStrategyRegistry(
      List.of(new GridSeatLayoutStrategy(), new RowSeatLayoutStrategy(), new GeneralAdmissionLayoutStrategy()));

  @Test
  void generalAdmissionRequiresPositiveCapacityAndGeneratesNoPhysicalSeats() {
    assertEquals(0, layouts.resolve("GENERAL_ADMISSION").generate("venue-1",
        new SeatLayoutRules("GENERAL_ADMISSION", "", null, null, null, null, List.of(), 25)).size());
    assertThrows(IllegalArgumentException.class, () -> layouts.resolve("GENERAL_ADMISSION")
        .generate("venue-1", new SeatLayoutRules("GENERAL_ADMISSION", "", null, null, null, null,
            List.of(), 0)));
  }

  @Test
  void gridLayoutGeneratesExpectedSeatCountAndCoordinates() {
    var seats = layouts.resolve("GRID").generate("venue-1", new SeatLayoutRules(
        "GRID", "看台", 2, 3, "LETTER", 1, List.of()));

    assertEquals(6, seats.size());
    assertEquals("A-1", seats.get(0).displayName());
    assertEquals("看台", seats.get(0).areaLabel());
    assertEquals(0, seats.get(0).y());
    assertEquals(1, seats.get(3).y());
  }

  @Test
  void gridLayoutNumbersRowsWhenRequested() {
    var seats = layouts.resolve("grid").generate("venue-1",
        new SeatLayoutRules("GRID", "", 2, 1, "NUMBER", 5, List.of()));

    assertEquals("1", seats.get(0).rowLabel());
    assertEquals(5, seats.get(0).seatNumber());
    assertEquals("2", seats.get(1).rowLabel());
  }

  @Test
  void gridLayoutRejectsNonPositiveDimensions() {
    assertThrows(IllegalArgumentException.class, () -> layouts.resolve("GRID")
        .generate("venue-1", new SeatLayoutRules("GRID", "", 0, 3, "LETTER", 1, List.of())));
  }

  @Test
  void rowsLayoutHonorsPerRowCounts() {
    var seats = layouts.resolve("ROWS").generate("venue-1", new SeatLayoutRules("ROWS", "VIP", null, null,
        null, null, List.of(new SeatLayoutRules.Row("A", 2, null), new SeatLayoutRules.Row("B", 3, null))));

    assertEquals(5, seats.size());
    assertEquals("A", seats.get(0).rowLabel());
    assertEquals("B", seats.get(2).rowLabel());
    assertEquals(1, seats.get(2).y());
    assertEquals(1, seats.get(4).y());
  }

  @Test
  void unknownModeListsTheRegisteredStrategies() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> layouts.resolve("FAN"));

    assertEquals(true, error.getMessage().contains("GRID"));
    assertEquals(true, error.getMessage().contains("ROWS"));
  }

  @Test
  void generatedSeatsAreAvailableRegularSeats() {
    VenueSeat seat = layouts.resolve("GRID")
        .generate("venue-1", new SeatLayoutRules("GRID", "", 1, 1, "LETTER", 1, List.of()))
        .get(0);

    assertEquals("REGULAR", seat.seatType());
    assertEquals("AVAILABLE", seat.status());
    assertEquals(true, seat.enabled());
  }
}

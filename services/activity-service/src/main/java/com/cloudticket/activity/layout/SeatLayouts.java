package com.cloudticket.activity.layout;

import com.cloudticket.activity.domain.VenueSeat;
import java.util.UUID;

/** Construction helpers shared by the layout strategies. */
final class SeatLayouts {

  static final String DEFAULT_SEAT_TYPE = "REGULAR";
  static final String INITIAL_STATUS = "AVAILABLE";

  private SeatLayouts() {}

  static VenueSeat seat(String venueId, String areaLabel, String rowLabel, int seatNumber, int x, int y) {
    return new VenueSeat(UUID.randomUUID().toString(), venueId.trim(), areaLabel, rowLabel, seatNumber,
        rowLabel + "-" + seatNumber, x, y, DEFAULT_SEAT_TYPE, true, INITIAL_STATUS);
  }

  /** 0 -> A, 25 -> Z, 26 -> AA. */
  static String letters(int index) {
    int value = index;
    StringBuilder label = new StringBuilder();
    while (value >= 0) {
      label.append((char) ('A' + value % 26));
      value = value / 26 - 1;
    }
    return label.reverse().toString();
  }
}

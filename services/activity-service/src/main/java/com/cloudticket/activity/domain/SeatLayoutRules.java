package com.cloudticket.activity.domain;

import java.util.List;

/**
 * Typed replacement for the {@code Map<String,Object>} rules the layout endpoint used to accept.
 *
 * <p>Passing an untyped map meant every strategy read field names by string, so a typo only failed at
 * runtime. The command object is bound once at the API boundary and the strategies work with fields.
 */
public record SeatLayoutRules(String mode, String areaLabel, Integer rowCount, Integer seatsPerRow,
                              String rowLabelType, Integer startSeatNumber, List<Row> rows,
                              Integer capacity) {

  public SeatLayoutRules(String mode, String areaLabel, Integer rowCount, Integer seatsPerRow,
                         String rowLabelType, Integer startSeatNumber, List<Row> rows) {
    this(mode, areaLabel, rowCount, seatsPerRow, rowLabelType, startSeatNumber, rows, null);
  }

  public SeatLayoutRules {
    mode = mode == null || mode.isBlank() ? "GRID" : mode.trim();
    areaLabel = areaLabel == null ? "" : areaLabel.trim();
    rowLabelType = rowLabelType == null ? "" : rowLabelType.trim();
    rows = rows == null ? List.of() : List.copyOf(rows);
    if (capacity != null && capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
  }

  public boolean lettersAsRowLabels() {
    return !"NUMBER".equalsIgnoreCase(rowLabelType);
  }

  public int startSeatNumberOr(int fallback) {
    return Math.max(1, startSeatNumber == null ? fallback : startSeatNumber);
  }

  public int capacityOrZero() {
    return capacity == null ? 0 : capacity;
  }

  public record Row(String rowLabel, Integer seatCount, Integer startSeatNumber) {
    public Row {
      rowLabel = rowLabel == null ? "" : rowLabel.trim();
      seatCount = seatCount == null ? 0 : seatCount;
      startSeatNumber = Math.max(1, startSeatNumber == null ? 1 : startSeatNumber);
    }
  }
}

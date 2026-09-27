package com.cloudticket.activity.layout;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.VenueSeat;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Uniform grid: every row has the same number of seats. */
@Component
public class GridSeatLayoutStrategy implements SeatLayoutStrategy {

  public static final String MODE = "GRID";

  @Override
  public String mode() {
    return MODE;
  }

  @Override
  public List<VenueSeat> generate(String venueId, SeatLayoutRules rules) {
    int rowCount = rules.rowCount() == null ? 0 : rules.rowCount();
    int seatsPerRow = rules.seatsPerRow() == null ? 0 : rules.seatsPerRow();
    if (rowCount <= 0 || seatsPerRow <= 0) {
      throw new IllegalArgumentException("rowCount and seatsPerRow must be positive");
    }
    boolean letteredRows = rules.lettersAsRowLabels();
    int startSeatNumber = rules.startSeatNumberOr(1);
    List<VenueSeat> layout = new ArrayList<>(rowCount * seatsPerRow);
    for (int row = 0; row < rowCount; row++) {
      String rowLabel = letteredRows ? SeatLayouts.letters(row) : String.valueOf(row + 1);
      for (int column = 0; column < seatsPerRow; column++) {
        layout.add(SeatLayouts.seat(venueId, rules.areaLabel(), rowLabel, startSeatNumber + column, column, row));
      }
    }
    return layout;
  }
}

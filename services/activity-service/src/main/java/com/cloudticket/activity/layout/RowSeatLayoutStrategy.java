package com.cloudticket.activity.layout;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.VenueSeat;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Every configured row declares its own seat count. */
@Component
public class RowSeatLayoutStrategy implements SeatLayoutStrategy {

  public static final String MODE = "ROWS";

  @Override
  public String mode() {
    return MODE;
  }

  @Override
  public List<VenueSeat> generate(String venueId, SeatLayoutRules rules) {
    List<VenueSeat> layout = new ArrayList<>();
    int y = 0;
    for (SeatLayoutRules.Row row : rules.rows()) {
      for (int index = 0; index < row.seatCount(); index++) {
        layout.add(SeatLayouts.seat(venueId, rules.areaLabel(), row.rowLabel(),
            row.startSeatNumber() + index, index, y));
      }
      y++;
    }
    return layout;
  }
}

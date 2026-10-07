package com.cloudticket.activity.layout;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.VenueSeat;
import java.util.List;
import org.springframework.stereotype.Component;

/** Capacity-only layout with no physical seats. */
@Component
public class GeneralAdmissionLayoutStrategy implements SeatLayoutStrategy {

  public static final String MODE = "GENERAL_ADMISSION";

  @Override
  public String mode() {
    return MODE;
  }

  @Override
  public List<VenueSeat> generate(String venueId, SeatLayoutRules rules) {
    if (rules.capacityOrZero() <= 0) {
      throw new IllegalArgumentException("capacity must be positive for GENERAL_ADMISSION");
    }
    if (venueId == null || venueId.isBlank()) throw new IllegalArgumentException("venueId required");
    return List.of();
  }
}

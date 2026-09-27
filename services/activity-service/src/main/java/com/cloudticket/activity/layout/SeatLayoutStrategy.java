package com.cloudticket.activity.layout;

import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.VenueSeat;
import java.util.List;

/**
 * Generates a venue seat template for one layout mode.
 *
 * <p>The mode used to be an {@code if/else} chain inside a single long method, so a new layout (a
 * fan-shaped stand or an in-the-round theatre) meant editing that method. Adding a layout is now a
 * new bean registered by {@link SeatLayoutStrategyRegistry}.
 */
public interface SeatLayoutStrategy {

  /** Mode code accepted by the API, for example {@code GRID}. */
  String mode();

  List<VenueSeat> generate(String venueId, SeatLayoutRules rules);
}

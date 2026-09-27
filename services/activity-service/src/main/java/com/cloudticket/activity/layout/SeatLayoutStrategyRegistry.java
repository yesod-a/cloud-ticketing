package com.cloudticket.activity.layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Selects the strategy for a requested layout mode; the registered beans are the allow-list. */
@Component
public class SeatLayoutStrategyRegistry {

  private final Map<String, SeatLayoutStrategy> strategies = new LinkedHashMap<>();

  public SeatLayoutStrategyRegistry(List<SeatLayoutStrategy> available) {
    for (SeatLayoutStrategy strategy : available) {
      strategies.put(strategy.mode().toUpperCase(Locale.ROOT), strategy);
    }
  }

  public SeatLayoutStrategy resolve(String mode) {
    SeatLayoutStrategy strategy = strategies.get(mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT));
    if (strategy == null) throw new IllegalArgumentException("mode must be one of " + strategies.keySet());
    return strategy;
  }

  public List<String> modes() {
    return List.copyOf(strategies.keySet());
  }
}

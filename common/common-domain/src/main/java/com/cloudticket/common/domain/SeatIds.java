package com.cloudticket.common.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The seat-selection rule shared by the order and inventory services.
 *
 * <p>Both services used to carry their own copy of "trim, drop blanks, reject duplicates, cap the
 * count at six", which is how the two copies drifted apart in wording and behaviour. A seat
 * selection is a domain rule, so it lives here once.
 */
public final class SeatIds {

  public static final int MAX_PER_SELECTION = 6;
  public static final String RULE_MESSAGE = "seatIds must contain 1-6 unique values";

  private SeatIds() {}

  /** Parses the comma separated form the order API accepts. */
  public static List<String> parse(String csv) {
    if (csv == null || csv.isBlank()) throw new IllegalArgumentException(RULE_MESSAGE);
    String[] parts = csv.split(",");
    List<String> seats = new ArrayList<>(parts.length);
    for (String part : parts) {
      String value = part == null ? "" : part.trim();
      if (!value.isBlank()) seats.add(value);
    }
    if (seats.size() != parts.length) throw new IllegalArgumentException(RULE_MESSAGE);
    return requireValid(seats);
  }

  /** Normalises the list form the inventory API accepts. */
  public static List<String> normalize(List<String> raw) {
    if (raw == null || raw.isEmpty()) throw new IllegalArgumentException(RULE_MESSAGE);
    List<String> seats = new ArrayList<>(raw.size());
    for (String value : raw) {
      String candidate = value == null ? "" : value.trim();
      if (candidate.isBlank()) throw new IllegalArgumentException(RULE_MESSAGE);
      seats.add(candidate);
    }
    return requireValid(seats);
  }

  public static String join(List<String> seats) {
    return String.join(",", seats);
  }

  private static List<String> requireValid(List<String> seats) {
    if (seats.isEmpty() || seats.size() > MAX_PER_SELECTION) throw new IllegalArgumentException(RULE_MESSAGE);
    if (new LinkedHashSet<>(seats).size() != seats.size()) throw new IllegalArgumentException(RULE_MESSAGE);
    return List.copyOf(seats);
  }
}

package com.cloudticket.activity.api.command;

/** Typed request bodies for the session admin API. */
public final class SessionCommands {

  private SessionCommands() {}

  public record CreateSession(String venueId, String startsAt, String endsAt, String status, Integer price,
                              String layoutMode, Integer capacity, Integer purchaseLimit) {
    public CreateSession(String venueId, String startsAt, String endsAt, String status, Integer price) {
      this(venueId, startsAt, endsAt, status, price, null, null, null);
    }
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "DRAFT" : status.trim();
    }

    public int priceOrZero() {
      return price == null ? 0 : price;
    }

    public String layoutModeOrDefault() { return layoutMode == null || layoutMode.isBlank() ? "GRID" : layoutMode.trim(); }
    public int capacityOrZero() { return capacity == null ? 0 : capacity; }
    public int purchaseLimitOrZero() { return purchaseLimit == null ? 0 : purchaseLimit; }
  }

  public record UpdateSession(String startsAt, String endsAt, String status, Integer price,
                              String layoutMode, Integer capacity, Integer purchaseLimit) {
    public UpdateSession(String startsAt, String endsAt, String status, Integer price) {
      this(startsAt, endsAt, status, price, null, null, null);
    }
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "DRAFT" : status.trim();
    }

    public int priceOrZero() {
      return price == null ? 0 : price;
    }

    public String layoutModeOrDefault() { return layoutMode == null || layoutMode.isBlank() ? "GRID" : layoutMode.trim(); }
    public int capacityOrZero() { return capacity == null ? 0 : capacity; }
    public int purchaseLimitOrZero() { return purchaseLimit == null ? 0 : purchaseLimit; }
  }

  public record UpdateSeat(String status) {
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "AVAILABLE" : status;
    }
  }
}

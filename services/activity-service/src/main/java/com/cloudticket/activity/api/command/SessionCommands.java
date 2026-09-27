package com.cloudticket.activity.api.command;

/** Typed request bodies for the session admin API. */
public final class SessionCommands {

  private SessionCommands() {}

  public record CreateSession(String venueId, String startsAt, String endsAt, String status, Integer price) {
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "DRAFT" : status.trim();
    }

    public int priceOrZero() {
      return price == null ? 0 : price;
    }
  }

  public record UpdateSession(String startsAt, String endsAt, String status, Integer price) {
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "DRAFT" : status.trim();
    }

    public int priceOrZero() {
      return price == null ? 0 : price;
    }
  }

  public record UpdateSeat(String status) {
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "AVAILABLE" : status;
    }
  }
}

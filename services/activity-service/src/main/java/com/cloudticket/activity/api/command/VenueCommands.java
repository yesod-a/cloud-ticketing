package com.cloudticket.activity.api.command;

/** Typed request bodies for the venue admin API. */
public final class VenueCommands {

  private VenueCommands() {}

  public record CreateVenue(String activityId, String name, String address) {}

  public record UpdateVenue(String name, String address) {}

  public record CreateVenueSeat(String areaLabel, String rowLabel, Integer seatNumber, String displayName,
                                Integer x, Integer y, String seatType, Boolean enabled) {
    public int seatNumberOrZero() {
      return seatNumber == null ? 0 : seatNumber;
    }

    public int xOrZero() {
      return x == null ? 0 : x;
    }

    public int yOrZero() {
      return y == null ? 0 : y;
    }

    public boolean enabledOrDefault() {
      return enabled == null || enabled;
    }
  }

  public record UpdateVenueSeat(String areaLabel, String rowLabel, Integer seatNumber, String displayName,
                                Integer x, Integer y, String seatType, Boolean enabled, String status) {
    public int seatNumberOrZero() {
      return seatNumber == null ? 0 : seatNumber;
    }

    public int xOrZero() {
      return x == null ? 0 : x;
    }

    public int yOrZero() {
      return y == null ? 0 : y;
    }

    public boolean enabledOrDefault() {
      return enabled == null || enabled;
    }
  }

}

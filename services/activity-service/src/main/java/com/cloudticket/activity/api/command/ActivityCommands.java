package com.cloudticket.activity.api.command;

/**
 * Typed request bodies for the activity admin API.
 *
 * <p>Bindings are records rather than {@code Map<String,String>} so a misspelled field fails at the
 * boundary instead of silently becoming {@code null} inside the use case.
 */
public final class ActivityCommands {

  private ActivityCommands() {}

  public record CreateActivity(String title, String organizer, String description) {
    public CreateActivity(String title, String organizer) { this(title, organizer, ""); }
  }

  public record UpdateActivity(String title, String organizer, String description) {
    public UpdateActivity(String title, String organizer) { this(title, organizer, ""); }
  }
}

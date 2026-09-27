package com.cloudticket.activity.persistence;

/** Small normalisation helpers shared by the repositories and services. */
public final class Text {

  private Text() {}

  public static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  public static String trimmedOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}

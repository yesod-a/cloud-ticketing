package com.cloudticket.inventory.api;

public final class CacheWarmupCommands {
  private CacheWarmupCommands() {}

  public record Upsert(String sessionId, String saleStartAt) {}
}

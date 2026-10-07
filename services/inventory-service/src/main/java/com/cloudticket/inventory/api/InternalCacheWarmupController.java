package com.cloudticket.inventory.api;

import com.cloudticket.common.security.RequireInternalToken;
import com.cloudticket.inventory.persistence.InventoryCacheWarmupRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireInternalToken
@RequestMapping("/api/internal/inventory/cache-warmups")
public class InternalCacheWarmupController {
  private final InventoryCacheWarmupRepository warmups;

  public InternalCacheWarmupController(InventoryCacheWarmupRepository warmups) {
    this.warmups = warmups;
  }

  @PostMapping
  public Map<String, Object> upsert(@RequestBody CacheWarmupCommands.Upsert command) {
    if (command == null || command.sessionId() == null || command.sessionId().isBlank()) {
      throw new IllegalArgumentException("sessionId required");
    }
    if (command.saleStartAt() == null || command.saleStartAt().isBlank()) {
      return Map.of("sessionId", command.sessionId(), "status", "SKIPPED");
    }
    warmups.upsert(command.sessionId(), Instant.parse(command.saleStartAt()));
    return Map.of("sessionId", command.sessionId(), "status", "PENDING");
  }
}

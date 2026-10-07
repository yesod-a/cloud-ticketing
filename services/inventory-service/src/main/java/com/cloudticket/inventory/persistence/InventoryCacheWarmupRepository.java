package com.cloudticket.inventory.persistence;

import com.cloudticket.inventory.persistence.entity.InventoryCacheWarmupEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryCacheWarmupMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryCacheWarmupRepository {
  private final InventoryCacheWarmupMapper mapper;
  private final Duration leadTime;

  @Autowired
  public InventoryCacheWarmupRepository(InventoryCacheWarmupMapper mapper) {
    this(mapper, 35);
  }

  public InventoryCacheWarmupRepository(InventoryCacheWarmupMapper mapper, long leadMinutes) {
    this.mapper = mapper;
    this.leadTime = Duration.ofMinutes(Math.max(1, leadMinutes));
  }

  public void upsert(String sessionId, Instant saleStartAt) {
    if (sessionId == null || sessionId.isBlank() || saleStartAt == null) return;
    mapper.upsert(sessionId, saleStartAt.minus(leadTime));
  }

  public List<InventoryCacheWarmupEntity> claimDue(String workerId, Instant now, Instant claimUntil, int limit) {
    List<InventoryCacheWarmupEntity> due = mapper.selectDue(now, Math.max(1, limit));
    return due.stream()
        .filter(row -> mapper.claim(row.getSessionId(), workerId, now, claimUntil) > 0)
        .toList();
  }

  public int markReady(String sessionId, String workerId, String version, Instant at) {
    return mapper.markReady(sessionId, workerId, version, at);
  }

  public int markFailed(String sessionId, String workerId, String error, Instant nextAttemptAt) {
    String bounded = error == null ? "unknown failure" : error.substring(0, Math.min(512, error.length()));
    return mapper.markFailed(sessionId, workerId, bounded, nextAttemptAt);
  }

  public int releaseExpiredClaims(Instant now) { return mapper.releaseExpiredClaims(now); }
}

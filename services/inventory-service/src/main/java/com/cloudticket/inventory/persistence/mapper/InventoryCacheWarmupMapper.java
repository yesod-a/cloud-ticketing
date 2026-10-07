package com.cloudticket.inventory.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.inventory.persistence.entity.InventoryCacheWarmupEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InventoryCacheWarmupMapper extends BaseMapper<InventoryCacheWarmupEntity> {
  @Insert("INSERT INTO inventory_cache_warmup(session_id,preheat_at,status,attempts) VALUES(#{sessionId},#{preheatAt},'PENDING',0) "
      + "ON DUPLICATE KEY UPDATE preheat_at=VALUES(preheat_at),status=IF(preheat_at<>VALUES(preheat_at),'PENDING',status),"
      + "cache_version=IF(preheat_at<>VALUES(preheat_at),NULL,cache_version),attempts=IF(preheat_at<>VALUES(preheat_at),0,attempts),"
      + "next_attempt_at=NULL,last_error=NULL,claimed_by=NULL,claim_until=NULL")
  int upsert(@Param("sessionId") String sessionId, @Param("preheatAt") Instant preheatAt);

  @Select("SELECT * FROM inventory_cache_warmup WHERE status IN ('PENDING','FAILED') AND preheat_at<=#{now} "
      + "AND (next_attempt_at IS NULL OR next_attempt_at<=#{now}) ORDER BY preheat_at LIMIT #{limit}")
  List<InventoryCacheWarmupEntity> selectDue(@Param("now") Instant now, @Param("limit") int limit);

  @Update("UPDATE inventory_cache_warmup SET status='RUNNING',claimed_by=#{workerId},claim_until=#{claimUntil} "
      + "WHERE session_id=#{sessionId} AND status IN ('PENDING','FAILED') AND preheat_at<=#{now} "
      + "AND (next_attempt_at IS NULL OR next_attempt_at<=#{now})")
  int claim(@Param("sessionId") String sessionId, @Param("workerId") String workerId,
            @Param("now") Instant now, @Param("claimUntil") Instant claimUntil);

  @Update("UPDATE inventory_cache_warmup SET status='READY',cache_version=#{version},preheated_at=#{at},"
      + "last_error=NULL,next_attempt_at=NULL,claimed_by=NULL,claim_until=NULL WHERE session_id=#{sessionId} "
      + "AND status='RUNNING' AND claimed_by=#{workerId}")
  int markReady(@Param("sessionId") String sessionId, @Param("workerId") String workerId,
                @Param("version") String version, @Param("at") Instant at);

  @Update("UPDATE inventory_cache_warmup SET status='FAILED',attempts=attempts+1,last_error=#{error},"
      + "next_attempt_at=#{nextAttemptAt},claimed_by=NULL,claim_until=NULL WHERE session_id=#{sessionId} "
      + "AND status='RUNNING' AND claimed_by=#{workerId}")
  int markFailed(@Param("sessionId") String sessionId, @Param("workerId") String workerId,
                 @Param("error") String error, @Param("nextAttemptAt") Instant nextAttemptAt);

  @Update("UPDATE inventory_cache_warmup SET status='FAILED',next_attempt_at=#{now},claimed_by=NULL,claim_until=NULL "
      + "WHERE status='RUNNING' AND claim_until<#{now}")
  int releaseExpiredClaims(@Param("now") Instant now);
}

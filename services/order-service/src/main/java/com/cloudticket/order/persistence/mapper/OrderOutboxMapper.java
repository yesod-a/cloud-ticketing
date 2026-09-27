package com.cloudticket.order.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.order.persistence.entity.OrderOutboxEntity;
import java.util.List;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OrderOutboxMapper extends BaseMapper<OrderOutboxEntity> {

  @Update("UPDATE order_outbox SET claim_token=#{token}, claimed_until=#{until} "
      + "WHERE published_at IS NULL AND (claimed_until IS NULL OR claimed_until < CURRENT_TIMESTAMP) "
      + "AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) "
      + "ORDER BY occurred_at,event_id LIMIT #{limit}")
  int claimPending(@Param("token") String token, @Param("until") Instant until, @Param("limit") int limit);

  @Select("SELECT * FROM order_outbox WHERE published_at IS NULL AND claim_token=#{token} "
      + "AND claimed_until >= CURRENT_TIMESTAMP ORDER BY occurred_at,event_id LIMIT #{limit}")
  List<OrderOutboxEntity> selectClaimed(@Param("token") String token, @Param("limit") int limit);

  @Update("UPDATE order_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL,claim_token=NULL,claimed_until=NULL,next_attempt_at=NULL "
      + "WHERE event_id=#{eventId} AND published_at IS NULL AND claim_token=#{token}")
  int markPublished(@Param("eventId") String eventId, @Param("token") String token);

  @Update("UPDATE order_outbox SET attempts = attempts + 1,last_error=#{message},claim_token=NULL,claimed_until=NULL, "
      + "next_attempt_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,LEAST(attempts,8))),CURRENT_TIMESTAMP) "
      + "WHERE event_id=#{eventId} AND claim_token=#{token}")
  int recordFailure(@Param("eventId") String eventId, @Param("message") String message,
                    @Param("token") String token);
}

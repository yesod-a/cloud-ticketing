package com.cloudticket.order.timeout;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OrderTimeoutOutboxMapper extends BaseMapper<OrderTimeoutOutboxEntity> {
  @Select("SELECT * FROM order_timeout_outbox WHERE published_at IS NULL "
      + "AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) "
      + "ORDER BY expire_at,created_at LIMIT #{limit}")
  List<OrderTimeoutOutboxEntity> pending(@Param("limit") int limit);

  @Update("UPDATE order_timeout_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL,next_attempt_at=NULL "
      + "WHERE id=#{id} AND published_at IS NULL")
  int markPublished(@Param("id") String id);

  @Update("UPDATE order_timeout_outbox SET attempts=attempts+1,last_error=#{error}, "
      + "next_attempt_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,LEAST(attempts,8))),CURRENT_TIMESTAMP) "
      + "WHERE id=#{id} AND published_at IS NULL")
  int markFailure(@Param("id") String id, @Param("error") String error);
}

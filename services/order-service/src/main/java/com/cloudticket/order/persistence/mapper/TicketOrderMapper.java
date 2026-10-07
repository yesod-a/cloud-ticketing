package com.cloudticket.order.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TicketOrderMapper extends BaseMapper<TicketOrderEntity> {

  @Select("SELECT id,user_id,session_id,activity_id,seat_ids,quantity,ticket_numbers,idempotency_key,request_hash,status,amount_minor,original_amount_minor,discount_amount_minor,coupon_id,coupon_reservation_id,created_at,updated_at "
      + "FROM ticket_order WHERE status='PENDING' AND created_at < TIMESTAMPADD(MINUTE, -#{minutes}, CURRENT_TIMESTAMP)")
  List<TicketOrderEntity> selectExpiredCandidates(@Param("minutes") int minutes);

  @Select("SELECT id,expire_at FROM ticket_order WHERE status='PENDING' AND expire_at IS NOT NULL "
      + "ORDER BY expire_at LIMIT #{limit}")
  List<TicketOrderEntity> selectPendingForTimeoutRebuild(@Param("limit") int limit);

  /**
   * Conditional transition. The predicate lives in the statement so that two scanners racing on the
   * same order cannot both publish an expiry event.
   */
  @Update("UPDATE ticket_order SET status='EXPIRED' WHERE id=#{id} AND status='PENDING' "
      + "AND created_at < TIMESTAMPADD(MINUTE, -#{minutes}, CURRENT_TIMESTAMP)")
  int expireIfStillPending(@Param("id") String id, @Param("minutes") int minutes);

  @Update("UPDATE ticket_order SET status='EXPIRED' WHERE id=#{id} AND status='PENDING' "
      + "AND expire_at IS NOT NULL AND expire_at <= CURRENT_TIMESTAMP")
  int expireIfStillPendingByDeadline(@Param("id") String id);

  @Update("UPDATE ticket_order SET status='CANCELLED' WHERE id=#{id} AND status='PENDING'")
  int cancelIfCancellable(@Param("id") String id);

  @Update("UPDATE ticket_order SET status='PAID' WHERE id=#{id} AND status='PENDING'")
  int markPaidIfPending(@Param("id") String id);

  @Update("UPDATE ticket_order SET status='REFUNDED' WHERE id=#{id} AND status='PAID'")
  int markRefundedIfPaid(@Param("id") String id);
}

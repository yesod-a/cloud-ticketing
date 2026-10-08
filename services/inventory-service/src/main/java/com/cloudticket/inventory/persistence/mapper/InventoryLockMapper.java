package com.cloudticket.inventory.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.inventory.persistence.entity.InventoryLockEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

public interface InventoryLockMapper extends BaseMapper<InventoryLockEntity> {

  @Insert("<script>INSERT INTO inventory_lock(id,order_id,session_id,seat_id,active,status,expires_at) VALUES <foreach collection='locks' item='lock' separator=','>(#{lock.id},#{lock.orderId},#{lock.sessionId},#{lock.seatId},#{lock.active},#{lock.status},#{lock.expiresAt})</foreach></script>")
  int insertBatch(@Param("locks") List<InventoryLockEntity> locks);

  @Select("SELECT DISTINCT order_id FROM inventory_lock WHERE active = 1 AND expires_at <= CURRENT_TIMESTAMP")
  List<String> selectExpiredOrderIds();

  @Select("SELECT l.seat_id FROM inventory_lock l JOIN inventory_seat s ON s.id=l.seat_id "
      + "WHERE l.session_id=#{sessionId} AND l.status='CONFIRMED' AND s.status='LOCKED'")
  List<String> selectConfirmedStillLockedSeatIds(@Param("sessionId") String sessionId);

  /**
   * Retires every active lock of an order.
   *
   * <p>The conditional update keeps release idempotent: a second call matches nothing and is a no-op.
   */
  @Update("UPDATE inventory_lock SET active = 0, status = #{status} WHERE order_id = #{orderId} AND active = 1")
  int retireActive(@Param("orderId") String orderId, @Param("status") String status);

  @Update("UPDATE inventory_lock SET status='ACTIVE', expires_at=#{expiresAt} WHERE order_id=#{orderId} AND active=1 AND status='PREPARED'")
  int promotePrepared(@Param("orderId") String orderId, @Param("expiresAt") java.time.Instant expiresAt);
}

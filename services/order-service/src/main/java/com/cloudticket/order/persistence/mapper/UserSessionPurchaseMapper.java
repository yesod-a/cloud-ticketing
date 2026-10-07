package com.cloudticket.order.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.order.persistence.entity.UserSessionPurchaseEntity;
import com.cloudticket.order.persistence.entity.UserSessionPurchaseReservationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface UserSessionPurchaseMapper extends BaseMapper<UserSessionPurchaseEntity> {
  @Insert("INSERT IGNORE INTO user_session_purchase(user_id,session_id,active_quantity,reserved_quantity) VALUES(#{userId},#{sessionId},0,0)")
  int ensure(@Param("userId") String userId, @Param("sessionId") String sessionId);
  @Update("UPDATE user_session_purchase SET reserved_quantity=reserved_quantity+#{quantity}, reservation_expires_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{ttl} SECOND) WHERE user_id=#{userId} AND session_id=#{sessionId} AND (#{limit}=0 OR active_quantity+reserved_quantity+#{quantity} <= #{limit})")
  int reserve(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("quantity") int quantity,
              @Param("limit") int limit, @Param("ttl") long ttl);
  @Update("UPDATE user_session_purchase SET reserved_quantity=GREATEST(0,reserved_quantity-#{quantity}) WHERE user_id=#{userId} AND session_id=#{sessionId}")
  int release(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("quantity") int quantity);
  @Update("UPDATE user_session_purchase SET reserved_quantity=GREATEST(0,reserved_quantity-#{quantity}), active_quantity=active_quantity+#{quantity} WHERE user_id=#{userId} AND session_id=#{sessionId} AND reserved_quantity >= #{quantity}")
  int activate(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("quantity") int quantity);
  @Update("UPDATE user_session_purchase SET active_quantity=GREATEST(0,active_quantity-#{quantity}) WHERE user_id=#{userId} AND session_id=#{sessionId}")
  int decrement(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("quantity") int quantity);

  @Insert("INSERT INTO user_session_purchase_reservation(order_id,user_id,session_id,quantity,state,expires_at) "
      + "VALUES(#{orderId},#{userId},#{sessionId},#{quantity},'HELD',DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{ttl} SECOND))")
  int insertReservation(@Param("orderId") String orderId, @Param("userId") String userId,
                        @Param("sessionId") String sessionId, @Param("quantity") int quantity,
                        @Param("ttl") long ttl);

  @Update("UPDATE user_session_purchase SET reserved_quantity=reserved_quantity+#{quantity}, "
      + "reservation_expires_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{ttl} SECOND) "
      + "WHERE user_id=#{userId} AND session_id=#{sessionId} "
      + "AND (#{limit}=0 OR active_quantity+reserved_quantity+#{quantity} <= #{limit})")
  int reserveAggregate(@Param("userId") String userId, @Param("sessionId") String sessionId,
                       @Param("quantity") int quantity, @Param("limit") int limit,
                       @Param("ttl") long ttl);

  @Select("SELECT * FROM user_session_purchase_reservation WHERE order_id=#{orderId}")
  UserSessionPurchaseReservationEntity findReservation(@Param("orderId") String orderId);

  @Select("SELECT r.* FROM user_session_purchase_reservation r LEFT JOIN ticket_order o ON o.id=r.order_id "
      + "WHERE r.state='HELD' AND r.expires_at IS NOT NULL AND r.expires_at <= CURRENT_TIMESTAMP "
      + "AND (o.id IS NULL OR o.status IN ('CANCELLED','EXPIRED','REFUNDED'))")
  List<UserSessionPurchaseReservationEntity> expiredReservations();

  @Update("UPDATE user_session_purchase_reservation SET state='RELEASED' WHERE order_id=#{orderId} AND state='HELD'")
  int releaseReservation(@Param("orderId") String orderId);

  @Update("UPDATE user_session_purchase_reservation SET state='RELEASED' WHERE order_id=#{orderId} AND state='ACTIVE'")
  int releaseActiveReservation(@Param("orderId") String orderId);

  @Update("UPDATE user_session_purchase_reservation SET state='ACTIVE' WHERE order_id=#{orderId} AND state='HELD'")
  int activateReservation(@Param("orderId") String orderId);

  @Update("DELETE FROM user_session_purchase_reservation WHERE order_id=#{orderId} AND state='HELD'")
  int deleteReservation(@Param("orderId") String orderId);

  @Update("UPDATE user_session_purchase SET reserved_quantity=GREATEST(0,reserved_quantity-#{quantity}) "
      + "WHERE user_id=#{userId} AND session_id=#{sessionId}")
  int decreaseReserved(@Param("userId") String userId, @Param("sessionId") String sessionId,
                       @Param("quantity") int quantity);

  @Update("UPDATE user_session_purchase SET active_quantity=active_quantity+#{quantity}, "
      + "reserved_quantity=GREATEST(0,reserved_quantity-#{quantity}) WHERE user_id=#{userId} AND session_id=#{sessionId}")
  int moveReservedToActive(@Param("userId") String userId, @Param("sessionId") String sessionId,
                           @Param("quantity") int quantity);

}

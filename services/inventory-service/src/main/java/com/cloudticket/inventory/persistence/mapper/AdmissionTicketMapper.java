package com.cloudticket.inventory.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.inventory.persistence.entity.AdmissionTicketEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AdmissionTicketMapper extends BaseMapper<AdmissionTicketEntity> {
  @Select("SELECT * FROM admission_ticket WHERE order_id=#{orderId} AND state='HELD' ORDER BY ticket_number")
  List<AdmissionTicketEntity> selectHeldByOrder(@Param("orderId") String orderId);

  @Select("SELECT * FROM admission_ticket WHERE order_id=#{orderId} AND state IN ('HELD','SOLD') ORDER BY ticket_number")
  List<AdmissionTicketEntity> selectActiveByOrder(@Param("orderId") String orderId);

  @Select("SELECT DISTINCT order_id FROM admission_ticket WHERE state='HELD' AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP")
  List<String> selectExpiredOrderIds();

  @Update("UPDATE admission_ticket SET state='SOLD' WHERE order_id=#{orderId} AND state='HELD'")
  int markSold(@Param("orderId") String orderId);

  @Update("UPDATE admission_ticket SET state='RELEASED' WHERE order_id=#{orderId} AND state='HELD'")
  int markReleased(@Param("orderId") String orderId);

  @Update("UPDATE admission_ticket SET state='RELEASED' WHERE order_id=#{orderId} AND state IN ('HELD','SOLD')")
  int markReleasedActive(@Param("orderId") String orderId);
}

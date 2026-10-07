package com.cloudticket.inventory.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.inventory.persistence.entity.InventoryReservationEntity;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;
public interface InventoryReservationMapper extends BaseMapper<InventoryReservationEntity> {
  @Update("UPDATE inventory_reservation SET status=#{toStatus},attempts=attempts+1,last_error=#{error},next_attempt_at=#{nextAttemptAt} WHERE reservation_id=#{id} AND status=#{fromStatus}")
  int transition(@Param("id")String id,@Param("fromStatus")String from,@Param("toStatus")String to,@Param("error")String error,@Param("nextAttemptAt")java.time.Instant next);
  @Update("UPDATE inventory_reservation SET seat_ids=#{seatIds}, seat_indexes=#{seatIndexes}, updated_at=CURRENT_TIMESTAMP WHERE reservation_id=#{id}")
  int updateSeatMapping(@Param("id") String id, @Param("seatIds") String seatIds, @Param("seatIndexes") String seatIndexes);
  @Select("SELECT * FROM inventory_reservation WHERE status='INVENTORY_HELD' AND expires_at<=#{now} ORDER BY expires_at LIMIT #{limit}")
  List<InventoryReservationEntity> expiredHeld(@Param("now") Instant now, @Param("limit") int limit);
}

package com.cloudticket.inventory.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.inventory.persistence.entity.InventoryOutboxEntity;
import java.time.Instant; import java.util.List;
import org.apache.ibatis.annotations.*;
public interface InventoryOutboxMapper extends BaseMapper<InventoryOutboxEntity> {
  @Select("SELECT * FROM inventory_outbox WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP) ORDER BY created_at LIMIT #{limit}")
  List<InventoryOutboxEntity> pending(@Param("limit") int limit);
  @Update("UPDATE inventory_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL,next_attempt_at=NULL WHERE event_id=#{id} AND published_at IS NULL") int markPublished(@Param("id") String id);
  @Update("UPDATE inventory_outbox SET attempts=attempts+1,last_error=#{error},next_attempt_at=#{next} WHERE event_id=#{id} AND published_at IS NULL") int markFailure(@Param("id") String id,@Param("error") String error,@Param("next") Instant next);
}

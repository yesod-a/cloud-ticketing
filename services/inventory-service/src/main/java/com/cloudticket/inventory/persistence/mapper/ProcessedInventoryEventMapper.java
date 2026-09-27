package com.cloudticket.inventory.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ProcessedInventoryEventMapper {

  @Insert("INSERT INTO processed_inventory_event(event_id,event_type,aggregate_id,trace_id) "
      + "VALUES(#{eventId},#{eventType},#{aggregateId},#{traceId})")
  int insert(@Param("eventId") String eventId, @Param("eventType") String eventType,
             @Param("aggregateId") String aggregateId, @Param("traceId") String traceId);
}

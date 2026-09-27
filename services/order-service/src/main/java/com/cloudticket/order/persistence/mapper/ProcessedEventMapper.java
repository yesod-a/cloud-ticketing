package com.cloudticket.order.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * Consumer-side idempotency guard.
 *
 * <p>{@code processed_event} has a composite primary key, so it deliberately does not extend
 * {@code BaseMapper}: the duplicate-key insert <em>is</em> the claim operation.
 */
public interface ProcessedEventMapper {

  @Insert("INSERT INTO processed_event(event_id,consumer_name,event_type,aggregate_id,payload_hash,trace_id) "
      + "VALUES (#{eventId},#{consumerName},#{eventType},#{aggregateId},#{payloadHash},#{traceId})")
  int claim(@Param("eventId") String eventId, @Param("consumerName") String consumerName,
            @Param("eventType") String eventType, @Param("aggregateId") String aggregateId,
            @Param("payloadHash") String payloadHash, @Param("traceId") String traceId);
}

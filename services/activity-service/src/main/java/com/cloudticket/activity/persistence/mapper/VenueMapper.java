package com.cloudticket.activity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.activity.persistence.entity.VenueEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface VenueMapper extends BaseMapper<VenueEntity> {

  /** Keeps the denormalised capacity column consistent with the seat template. */
  @Update("UPDATE venue SET capacity = (SELECT COUNT(*) FROM venue_seat WHERE venue_id = #{venueId}) "
      + "WHERE id = #{venueId}")
  int refreshCapacity(@Param("venueId") String venueId);
}

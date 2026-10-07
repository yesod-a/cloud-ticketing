package com.cloudticket.activity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ActivityImageMapper extends BaseMapper<ActivityImageEntity> {
  @Select("SELECT id FROM activity WHERE id = #{activityId} FOR UPDATE")
  String lockActivity(@Param("activityId") String activityId);
}

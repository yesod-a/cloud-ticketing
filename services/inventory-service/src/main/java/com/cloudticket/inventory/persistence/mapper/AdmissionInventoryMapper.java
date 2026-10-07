package com.cloudticket.inventory.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.inventory.persistence.entity.AdmissionInventoryEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

public interface AdmissionInventoryMapper extends BaseMapper<AdmissionInventoryEntity> {
  @Select("SELECT * FROM admission_inventory WHERE session_id = #{sessionId} FOR UPDATE")
  AdmissionInventoryEntity selectForUpdate(@Param("sessionId") String sessionId);
}

package com.cloudticket.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.auth.persistence.entity.AuthAuditLogEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AuthAuditLogMapper extends BaseMapper<AuthAuditLogEntity> {

  @Select("SELECT id,actor_user_id,action,resource_type,resource_id,trace_id,created_at FROM auth_audit_log "
      + "WHERE (#{action} = '' OR action = #{action}) ORDER BY created_at DESC")
  Page<AuthAuditLogEntity> selectPageByAction(Page<AuthAuditLogEntity> page, @Param("action") String action);
}

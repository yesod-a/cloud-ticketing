package com.cloudticket.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.auth.persistence.entity.AuthScopeEntity;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthScopeMapper extends BaseMapper<AuthScopeEntity> {

  /** @return the created scope, or {@code null} when the same resource already has one */
  @Select("SELECT * FROM auth_scope WHERE resource_type = #{resourceType} AND resource_id = #{resourceId}")
  AuthScopeEntity findByResource(@Param("resourceType") String resourceType,
                                 @Param("resourceId") UUID resourceId);

  @Select("SELECT s.id,s.resource_type,s.resource_id,s.status,s.created_at,"
      + "(SELECT COUNT(*) FROM auth_user_scope us WHERE us.scope_id = s.id) AS user_count "
      + "FROM auth_scope s WHERE (#{resourceType} = '' OR s.resource_type = #{resourceType}) "
      + "AND (#{status} = '' OR s.status = #{status}) ORDER BY s.resource_type,s.resource_id")
  @Results({
      @Result(column = "id", property = "id", id = true),
      @Result(column = "resource_type", property = "resourceType"),
      @Result(column = "resource_id", property = "resourceId"),
      @Result(column = "status", property = "status"),
      @Result(column = "created_at", property = "createdAt"),
      @Result(column = "user_count", property = "userCount")
  })
  Page<AuthScopeEntity> selectAdminPage(Page<AuthScopeEntity> page,
                                        @Param("resourceType") String resourceType,
                                        @Param("status") String status);

  @Insert("INSERT IGNORE INTO auth_user_scope(user_id,scope_id) VALUES(#{userId},#{scopeId})")
  int bind(@Param("userId") UUID userId, @Param("scopeId") UUID scopeId);

  @org.apache.ibatis.annotations.Delete("DELETE FROM auth_user_scope WHERE user_id = #{userId} "
      + "AND scope_id = #{scopeId}")
  int unbind(@Param("userId") UUID userId, @Param("scopeId") UUID scopeId);

  @Update("UPDATE auth_user SET scope_version = scope_version + 1 WHERE id = #{userId}")
  int bumpScopeVersion(@Param("userId") UUID userId);
}

package com.cloudticket.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.auth.persistence.entity.AuthRefreshTokenEntity;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthRefreshTokenMapper extends BaseMapper<AuthRefreshTokenEntity> {

  @Select("SELECT * FROM auth_refresh_token WHERE token_hash = #{tokenHash} AND revoked_at IS NULL "
      + "AND expires_at > CURRENT_TIMESTAMP(6) LIMIT 1")
  AuthRefreshTokenEntity findActiveByHash(@Param("tokenHash") String tokenHash);

  @Select("SELECT * FROM auth_refresh_token WHERE token_hash = #{tokenHash} LIMIT 1")
  AuthRefreshTokenEntity findByHash(@Param("tokenHash") String tokenHash);

  @Update("UPDATE auth_refresh_token SET revoked_at = #{revokedAt} WHERE id = #{id} AND revoked_at IS NULL")
  int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);

  @Update("UPDATE auth_refresh_token SET revoked_at = #{revokedAt} WHERE family_id = #{familyId} "
      + "AND revoked_at IS NULL")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}

package com.cloudticket.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.auth.persistence.entity.AuthRevokedAccessTokenEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AuthRevokedAccessTokenMapper extends BaseMapper<AuthRevokedAccessTokenEntity> {

  @Insert("INSERT IGNORE INTO auth_revoked_access_token(jti,expires_at) VALUES(#{jti},#{expiresAt})")
  int revoke(@Param("jti") String jti, @Param("expiresAt") java.time.Instant expiresAt);

  @Select("SELECT COUNT(*) FROM auth_revoked_access_token WHERE jti = #{jti} "
      + "AND expires_at > CURRENT_TIMESTAMP(6)")
  int countActiveRevocations(@Param("jti") String jti);
}

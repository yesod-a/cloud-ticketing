package com.cloudticket.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.auth.persistence.entity.AuthRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface AuthRoleMapper extends BaseMapper<AuthRoleEntity> {

  @Select("SELECT id,code,name,status FROM auth_role ORDER BY code")
  List<AuthRoleEntity> selectAllOrdered();

  @Insert("INSERT IGNORE INTO auth_role_permission(role_id, permission_id) "
      + "SELECT r.id, p.id FROM auth_role r CROSS JOIN auth_permission p WHERE r.code = 'SUPER_ADMIN'")
  int grantAllPermissionsToSuperAdmin();
}

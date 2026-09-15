package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.PermissionEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface PermissionRepository extends CrudRepository<PermissionEntity, UUID> {}

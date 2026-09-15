package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.RoleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface RoleRepository extends CrudRepository<RoleEntity, UUID> { Optional<RoleEntity> findByCode(String code); }

package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<UserEntity, UUID> {
    Optional<UserEntity> findByPhoneOrEmail(String phone, String email);
    default Optional<UserEntity> findByPhoneOrEmail(String identifier) { return findByPhoneOrEmail(identifier, identifier); }
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
}

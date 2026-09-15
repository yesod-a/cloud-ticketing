package com.cloudticket.auth.domain;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("auth_permission")
public record PermissionEntity(@Id UUID id, String code, String name) {}

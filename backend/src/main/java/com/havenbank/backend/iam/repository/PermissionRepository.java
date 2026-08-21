package com.havenbank.backend.iam.repository;

import com.havenbank.backend.iam.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
}

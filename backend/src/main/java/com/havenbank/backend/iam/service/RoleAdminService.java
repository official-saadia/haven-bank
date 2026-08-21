package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.Permission;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.dto.PermissionResponse;
import com.havenbank.backend.iam.dto.RoleRequest;
import com.havenbank.backend.iam.dto.RoleResponse;
import com.havenbank.backend.iam.repository.PermissionRepository;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import com.havenbank.backend.iam.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administrative management of roles and their permissions (ADMIN only). Every change is audited.
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    /**
     * Structural roles the app's authorization depends on. They can't be deleted or re-permissioned.
     */
    private static final Set<String> SYSTEM_ROLES = Set.of("CUSTOMER", "STAFF", "ADMIN");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;
    private final AuditService auditService;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAllWithPermissions().stream().map(roleMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissions.findAll().stream()
                .map(roleMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse create(UUID actor, RoleRequest request) {
        if (roles.existsByName(request.name())) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT, "Role already exists");
        }
        Role role = Role.builder().name(request.name()).description(request.description()).build();
        roles.save(role);
        auditService.record(AuditEvent.success(actor, AuditAction.ROLE_CREATED, request.name()));
        return roleMapper.toResponse(role);
    }

    @Transactional
    public RoleResponse update(UUID actor, UUID roleId, RoleRequest request) {
        Role role = role(roleId);
        role.updateDescription(request.description());
        auditService.record(AuditEvent.success(actor, AuditAction.ROLE_UPDATED, role.getName()));
        return roleMapper.toResponse(role);
    }

    @Transactional
    public void delete(UUID actor, UUID roleId) {
        Role role = role(roleId);
        if (SYSTEM_ROLES.contains(role.getName())) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "System roles cannot be deleted");
        }
        if (users.countUsersWithRole(roleId) > 0) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "Role is still assigned to one or more users");
        }
        roles.delete(role);
        auditService.record(AuditEvent.success(actor, AuditAction.ROLE_DELETED, role.getName()));
    }

    @Transactional
    public RoleResponse setPermissions(UUID actor, UUID roleId, List<UUID> permissionIds) {
        Role role = role(roleId);
        if (SYSTEM_ROLES.contains(role.getName())) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "Permissions of a system role cannot be changed");
        }
        Set<Permission> perms = new HashSet<>(permissions.findAllById(permissionIds));
        role.replacePermissions(perms);
        auditService.record(AuditEvent.success(actor, AuditAction.ROLE_PERMISSIONS_UPDATED, role.getName()));
        return roleMapper.toResponse(role);
    }

    private Role role(UUID id) {
        return roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

}
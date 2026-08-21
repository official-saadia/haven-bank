package com.havenbank.backend.iam.mapper;

import com.havenbank.backend.iam.domain.Permission;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.dto.PermissionResponse;
import com.havenbank.backend.iam.dto.RoleResponse;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Maps {@link Role} and {@link Permission} aggregates to their read projections. See
 * {@link UserMapper} for the mapping convention.
 */
@Component
public class RoleMapper {

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet()));
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }
}

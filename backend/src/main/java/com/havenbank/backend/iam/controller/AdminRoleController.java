package com.havenbank.backend.iam.controller;

import com.havenbank.backend.iam.dto.PermissionResponse;
import com.havenbank.backend.iam.dto.RolePermissionsRequest;
import com.havenbank.backend.iam.dto.RoleRequest;
import com.havenbank.backend.iam.dto.RoleResponse;
import com.havenbank.backend.iam.service.RoleAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.List;
import java.util.UUID;

/**
 * Role and permission administration (ADMIN only).
 */
@Tag(name = "Admin: roles", description = "Role and permission administration (ADMIN only). System roles are protected.")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleAdminService roleAdmin;

    @Operation(summary = "List roles", description = "Returns all roles and their permissions.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return roleAdmin.list();
    }

    @Operation(summary = "List permissions", description = "Returns the permission catalogue.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return roleAdmin.listPermissions();
    }

    @Operation(summary = "Create a role", description = "Creates a new (non-system) role.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(201).body(roleAdmin.create(actor(jwt), request));
    }

    @Operation(summary = "Update a role", description = "Renames or re-describes a role.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated role"),
            @ApiResponse(responseCode = "404", description = "No such role", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @PutMapping("/roles/{id}")
    public RoleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody RoleRequest request) {
        return roleAdmin.update(actor(jwt), id, request);
    }

    @Operation(summary = "Delete a role", description = "Deletes a role. System roles (CUSTOMER/STAFF/ADMIN) cannot be" +
            " deleted, nor can a role still assigned to users.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Role deleted"),
            @ApiResponse(responseCode = "404", description = "No such role", content = @Content()),
            @ApiResponse(responseCode = "409", description = "System role, or role still assigned", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        roleAdmin.delete(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set a role's permissions", description = "Replaces a role's permission set." +
            " A system role's permissions cannot be changed.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated role"),
            @ApiResponse(responseCode = "404", description = "No such role", content = @Content()),
            @ApiResponse(responseCode = "409", description = "Permissions of a system role cannot be changed",
                    content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @PutMapping("/roles/{id}/permissions")
    public RoleResponse setPermissions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @RequestBody RolePermissionsRequest request) {
        return roleAdmin.setPermissions(actor(jwt), id, request.permissionIds());
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
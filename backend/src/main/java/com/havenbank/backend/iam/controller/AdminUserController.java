package com.havenbank.backend.iam.controller;

import com.havenbank.backend.iam.dto.AdminUserResponse;
import com.havenbank.backend.iam.dto.UserRolesRequest;
import com.havenbank.backend.iam.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.UUID;

/**
 * User administration (ADMIN only). Deactivation is soft; users are never hard-deleted.
 */
@Tag(name = "Admin: users", description = "User listing and lifecycle: roles, lock/unlock, close (ADMIN only).")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdmin;

    @Operation(summary = "List users", description = "Paginated list of all users.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping
    public Page<AdminUserResponse> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return userAdmin.list(PageRequest.of(page, Math.min(size, 100)));
    }

    @Operation(summary = "Get a user", description = "Fetches one user by id.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The user"),
            @ApiResponse(responseCode = "404", description = "No such user", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return userAdmin.get(id);
    }

    @Operation(summary = "Set a user's roles", description = "Replaces a user's role set. CUSTOMER cannot be combined" +
            " with STAFF/ADMIN, an admin cannot edit their own roles, a user must keep at least one role, and " +
            "a closed account cannot be re-roled.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated user"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content()),
            @ApiResponse(responseCode = "404", description = "No such user", content = @Content()),
            @ApiResponse(responseCode = "409", description = "Role combination or state not allowed", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @PutMapping("/{id}/roles")
    public AdminUserResponse setRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody UserRolesRequest request) {
        return userAdmin.setRoles(actor(jwt), id, request.roleIds());
    }

    @Operation(summary = "Lock a user", description = "Prevents the user from signing in. A closed account cannot be locked.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "User locked"),
            @ApiResponse(responseCode = "404", description = "No such user", content = @Content()),
            @ApiResponse(responseCode = "409", description = "A closed account cannot be locked", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/{id}/lock")
    public ResponseEntity<Void> lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userAdmin.lock(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unlock a user", description = "Restores a locked user (to active, or pending verification if" +
            " never verified). Only a locked account can be unlocked.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "User unlocked"),
            @ApiResponse(responseCode = "404", description = "No such user", content = @Content()),
            @ApiResponse(responseCode = "409", description = "Only a locked account can be unlocked", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userAdmin.unlock(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Close a user", description = "Permanently closes the account. Terminal: a closed account cannot " +
            "be reactivated. Triggers a security-critical closure notification.")
    @ApiResponse(responseCode = "204", description = "Account closed")
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userAdmin.deactivate(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
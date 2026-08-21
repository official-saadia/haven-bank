package com.havenbank.backend.iam.controller;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.shared.security.TokenDenylist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Logout. Denylists the presented access token's {@code jti} for the remainder of its lifetime so it
 * can no longer be used, and audits the event (FR-1.9). The refresh token should be revoked by the
 * client via the standard {@code /oauth2/revoke} endpoint.
 */
@Tag(name = "Logout", description = "Denylists the presented access token for its remaining lifetime (FR-1.9).")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final TokenDenylist denylist;
    private final AuditService auditService;

    @Operation(summary = "Log out", description = "Adds the access token's jti to the denylist and audits the event. " +
            "Revoke the refresh token separately via /oauth2/revoke.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (jwt.getId() != null && expiresAt != null) {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            denylist.denylist(jwt.getId(), ttl);
        }
        auditService.record(AuditEvent.success(UUID.fromString(jwt.getSubject()),
                AuditAction.LOGOUT, "logout"));
        return ResponseEntity.noContent().build();
    }
}
package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.shared.security.TokenDenylist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Logout. Denylists the presented access token's {@code jti} for the remainder of its lifetime so it
 * can no longer be used, and audits the event (FR-1.9). The refresh token is revoked separately by
 * the client via the standard {@code /oauth2/revoke} endpoint.
 */
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final TokenDenylist denylist;
    private final AuditService auditService;

    /**
     * @param userId               the subject to record on the audit trail
     * @param jti                  the access token id to revoke (may be null if the token carried none)
     * @param accessTokenExpiresAt token expiry; the denylist entry lives only until then, so it
     *                             self-cleans (null when the token has no expiry claim)
     */
    public void logout(UUID userId, String jti, Instant accessTokenExpiresAt) {
        if (jti != null && accessTokenExpiresAt != null) {
            denylist.denylist(jti, Duration.between(Instant.now(), accessTokenExpiresAt));
        }
        auditService.record(AuditEvent.success(userId, AuditAction.LOGOUT, "logout"));
    }
}
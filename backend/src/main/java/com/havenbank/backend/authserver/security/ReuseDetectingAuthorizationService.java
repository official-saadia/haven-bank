package com.havenbank.backend.authserver.security;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Wraps the persistent {@link OAuth2AuthorizationService} to add refresh-token reuse detection with
 * family revocation (RFC 9700). Rotation itself is done by Spring Authorization Server; this adds the
 * missing half:
 *
 * <ul>
 *   <li>On every rotation ({@link #save}) the previous refresh token for the authorization is marked
 *       consumed and the new one is recorded under the same {@code family_id} (a fresh login starts a
 *       new family).</li>
 *   <li>When a presented refresh token is not active ({@link #findByToken} returns {@code null}) but
 *       is a known <em>consumed</em> token, that is a replay: <strong>all</strong> of the user's
 *       authorizations are revoked (max-safety policy), forcing full re-authentication.</li>
 * </ul>
 *
 * Only a SHA-256 hash of each token is stored. Lineage lives in Postgres so revocation state is
 * durable and shared across instances.
 */
public class ReuseDetectingAuthorizationService implements OAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ReuseDetectingAuthorizationService.class);

    private final OAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public ReuseDetectingAuthorizationService(OAuth2AuthorizationService delegate, JdbcTemplate jdbc,
                                              AuditService auditService) {
        this.delegate = delegate;
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken != null) {
            recordRotation(authorization.getId(), authorization.getPrincipalName(),
                    refreshToken.getToken().getTokenValue());
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        jdbc.update("DELETE FROM oauth2_refresh_token_family WHERE authorization_id = ?", authorization.getId());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization found = delegate.findByToken(token, tokenType);
        if (found != null) {
            return found;
        }
        // Not active. If it's a known, already-consumed refresh token, this is a replay.
        if (tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            detectReuse(token);
        }
        return null;
    }

    /** Mark the authorization's prior refresh token consumed and record the new one in the same family. */
    private void recordRotation(String authorizationId, String principalName, String tokenValue) {
        String hash = sha256(tokenValue);
        Integer already = jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_refresh_token_family WHERE token_hash = ?", Integer.class, hash);
        if (already != null && already > 0) {
            return; // idempotent: this token is already recorded
        }
        List<UUID> activeFamilies = jdbc.query(
                "SELECT family_id FROM oauth2_refresh_token_family WHERE authorization_id = ? AND consumed = false",
                (rs, i) -> rs.getObject("family_id", UUID.class), authorizationId);
        UUID familyId;
        if (!activeFamilies.isEmpty()) {
            familyId = activeFamilies.get(0); // rotation: continue the existing family
            jdbc.update("UPDATE oauth2_refresh_token_family SET consumed = true "
                    + "WHERE authorization_id = ? AND consumed = false", authorizationId);
        } else {
            familyId = UUID.randomUUID(); // fresh login: new family
        }
        jdbc.update("INSERT INTO oauth2_refresh_token_family "
                        + "(token_hash, family_id, authorization_id, principal_name, consumed) VALUES (?, ?, ?, ?, false)",
                hash, familyId, authorizationId, principalName);
    }

    /** If the token is a known consumed refresh token, revoke every authorization for that principal. */
    private void detectReuse(String token) {
        String hash = sha256(token);
        List<String> principals = jdbc.query(
                "SELECT principal_name FROM oauth2_refresh_token_family WHERE token_hash = ? AND consumed = true",
                (rs, i) -> rs.getString("principal_name"), hash);
        if (principals.isEmpty()) {
            return; // unknown token — not a detectable reuse, let the normal invalid_grant stand
        }
        String principalName = principals.get(0);
        int revoked = jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", principalName);
        jdbc.update("DELETE FROM oauth2_refresh_token_family WHERE principal_name = ?", principalName);
        log.warn("Refresh token reuse detected; revoked {} authorization(s) for the affected principal", revoked);
        auditService.record(AuditEvent.failure(null, AuditAction.REFRESH_TOKEN_REUSE,
                "refresh token reuse detected; all sessions revoked"));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never happens on a standard JVM
        }
    }
}
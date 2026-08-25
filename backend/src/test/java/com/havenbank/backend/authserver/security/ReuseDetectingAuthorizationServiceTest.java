package com.havenbank.backend.authserver.security;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FR-1.10, RFC 9700: rotation records lineage, and replaying a consumed refresh token revokes
 * <em>every</em> authorization for the principal - the max-safety policy documented on the class
 * itself. Already proven end to end by {@code RefreshTokenReuseIntegrationTest} against real
 * Postgres; this isolates each branch (fresh login vs. rotation vs. replay vs. an unrelated unknown
 * token vs. an access-token lookup) individually, which the integration test's one real scenario
 * doesn't attempt to do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReuseDetectingAuthorizationServiceTest {

    private static final String COUNT_SQL =
            "SELECT count(*) FROM oauth2_refresh_token_family WHERE token_hash = ?";
    private static final String ACTIVE_FAMILY_SQL =
            "SELECT family_id FROM oauth2_refresh_token_family WHERE authorization_id = ? AND consumed = false";
    private static final String CONSUME_SQL =
            "UPDATE oauth2_refresh_token_family SET consumed = true "
                    + "WHERE authorization_id = ? AND consumed = false";
    private static final String INSERT_SQL =
            "INSERT INTO oauth2_refresh_token_family "
                    + "(token_hash, family_id, authorization_id, principal_name, consumed) VALUES (?, ?, ?, ?, false)";
    private static final String CONSUMED_PRINCIPAL_SQL =
            "SELECT principal_name FROM oauth2_refresh_token_family WHERE token_hash = ? AND consumed = true";

    @Mock
    private OAuth2AuthorizationService delegate;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private AuditService auditService;

    private ReuseDetectingAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new ReuseDetectingAuthorizationService(delegate, jdbc, auditService);
        when(jdbc.queryForObject(eq(COUNT_SQL), eq(Integer.class), any())).thenReturn(0);
        when(jdbc.query(eq(ACTIVE_FAMILY_SQL), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of());
    }

    @Test
    void savingAFreshAuthorizationStartsANewFamily() {
        OAuth2Authorization authorization = authorizationWithRefreshToken("auth-1", "alice", "rt-1");

        service.save(authorization);

        verify(delegate).save(authorization);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq(INSERT_SQL), args.capture());
        assertThat(args.getValue()[2]).isEqualTo("auth-1");
        assertThat(args.getValue()[3]).isEqualTo("alice");
        // No prior family existed (stubbed empty above), so nothing gets marked consumed.
        verify(jdbc, never()).update(eq(CONSUME_SQL), any());
    }

    @Test
    void rotatingAnExistingFamilyConsumesThePreviousTokenAndKeepsTheSameFamilyId() {
        UUID existingFamilyId = UUID.randomUUID();
        when(jdbc.query(eq(ACTIVE_FAMILY_SQL), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of(existingFamilyId));

        OAuth2Authorization authorization = authorizationWithRefreshToken("auth-1", "alice", "rt-2");
        service.save(authorization);

        verify(jdbc).update(eq(CONSUME_SQL), eq("auth-1"));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq(INSERT_SQL), args.capture());
        assertThat(args.getValue()[1]).isEqualTo(existingFamilyId); // family id carried forward
    }

    @Test
    void savingTheSameTokenTwiceIsIdempotentAndDoesNotDuplicateTheRow() {
        when(jdbc.queryForObject(eq(COUNT_SQL), eq(Integer.class), any())).thenReturn(1); // already recorded

        service.save(authorizationWithRefreshToken("auth-1", "alice", "rt-1"));

        verify(jdbc, never()).update(eq(INSERT_SQL), any());
        verify(jdbc, never()).update(eq(CONSUME_SQL), any());
    }

    @Test
    void savingAnAuthorizationWithNoRefreshTokenDoesNotTouchTheFamilyTable() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getRefreshToken()).thenReturn(null);

        service.save(authorization);

        verify(delegate).save(authorization);
        verifyNoInteractions(jdbc);
    }

    @Test
    void findByTokenReturnsDirectlyWhenTheDelegateFindsAnActiveOne() {
        OAuth2Authorization found = mock(OAuth2Authorization.class);
        when(delegate.findByToken("active-token", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(found);

        OAuth2Authorization result = service.findByToken("active-token", OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isSameAs(found);
        // An active token is not a replay - reuse detection must never even query for it.
        verify(jdbc, never()).query(eq(CONSUMED_PRINCIPAL_SQL), any(org.springframework.jdbc.core.RowMapper.class), any());
    }

    @Test
    void replayingAKnownConsumedRefreshTokenRevokesEveryAuthorizationForThatPrincipal() {
        when(delegate.findByToken("stolen-token", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);
        when(jdbc.query(eq(CONSUMED_PRINCIPAL_SQL), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of("alice"));

        OAuth2Authorization result = service.findByToken("stolen-token", OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isNull();
        verify(jdbc).update("DELETE FROM oauth2_authorization WHERE principal_name = ?", "alice");
        verify(jdbc).update("DELETE FROM oauth2_refresh_token_family WHERE principal_name = ?", "alice");
        verify(auditService).record(argThat(event -> event.action() == AuditAction.REFRESH_TOKEN_REUSE));
    }

    @Test
    void anEntirelyUnknownTokenTriggersNoRevocationAndNoAudit() {
        when(delegate.findByToken("never-issued", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);
        when(jdbc.query(eq(CONSUMED_PRINCIPAL_SQL), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of()); // not a known consumed token at all

        OAuth2Authorization result = service.findByToken("never-issued", OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isNull();
        verify(jdbc, never()).update(startsWith("DELETE FROM oauth2_authorization"), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void anAccessTokenLookupNeverTriggersReuseDetectionEvenIfTheStringHappensToMatch() {
        when(delegate.findByToken("some-value", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);

        OAuth2Authorization result = service.findByToken("some-value", OAuth2TokenType.ACCESS_TOKEN);

        assertThat(result).isNull();
        // Reuse detection is specifically about refresh tokens - an access-token miss is just a
        // miss, not a replay signal.
        verify(jdbc, never()).query(eq(CONSUMED_PRINCIPAL_SQL), any(org.springframework.jdbc.core.RowMapper.class), any());
    }

    @Test
    void removingAnAuthorizationAlsoDeletesItsFamilyRows() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getId()).thenReturn("auth-1");

        service.remove(authorization);

        verify(delegate).remove(authorization);
        verify(jdbc).update("DELETE FROM oauth2_refresh_token_family WHERE authorization_id = ?", "auth-1");
    }

    private OAuth2Authorization authorizationWithRefreshToken(String id, String principal, String tokenValue) {
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(tokenValue, Instant.now());
        return OAuth2Authorization.withRegisteredClient(RegisteredClient.withId("client-1")
                        .clientId("spa").authorizationGrantType(
                                org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:5173/oauth/callback").build())
                .id(id)
                .principalName(principal)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .refreshToken(refreshToken)
                .build();
    }
}

package com.havenbank.backend.iam.security;

import com.havenbank.backend.shared.security.TokenDenylist;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A logged-out token must stop working before it expires (FR-1.9).
 */
@ExtendWith(MockitoExtension.class)
class DenylistJwtValidatorTest {

    @Mock
    private TokenDenylist denylist;
    @InjectMocks
    private DenylistJwtValidator validator;

    private Jwt jwtWithId(String jti) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .jti(jti)
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    void acceptsTokenThatHasNotBeenRevoked() {
        when(denylist.isDenylisted("jti-live")).thenReturn(false);
        assertThat(validator.validate(jwtWithId("jti-live")).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWhoseIdWasDenylistedOnLogout() {
        when(denylist.isDenylisted("jti-revoked")).thenReturn(true);
        OAuth2TokenValidatorResult result = validator.validate(jwtWithId("jti-revoked"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "token_revoked".equals(e.getErrorCode()));
    }
}

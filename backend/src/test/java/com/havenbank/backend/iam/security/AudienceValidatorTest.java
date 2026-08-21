package com.havenbank.backend.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A token must name this API in its {@code aud} claim (NFR-1.3).
 */
class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("haven-bank-api");

    private Jwt jwtWithAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    void acceptsTokenIssuedForThisApi() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("haven-bank-api")));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsWhenThisApiIsOneOfSeveralAudiences() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithAudience(List.of("other-api", "haven-bank-api")));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenIssuedForAnotherApi() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("some-other-api")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_token".equals(e.getErrorCode()));
    }

    @Test
    void rejectsTokenWithNoAudience() {
        assertThat(validator.validate(jwtWithAudience(null)).hasErrors()).isTrue();
    }
}

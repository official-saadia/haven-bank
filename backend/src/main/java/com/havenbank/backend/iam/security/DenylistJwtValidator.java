package com.havenbank.backend.iam.security;

import com.havenbank.backend.shared.security.TokenDenylist;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects any access token whose {@code jti} has been denylisted by logout (FR-1.9).
 */
@Component
@RequiredArgsConstructor
public class DenylistJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenDenylist denylist;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (denylist.isDenylisted(token.getId())) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("token_revoked", "The token has been revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}

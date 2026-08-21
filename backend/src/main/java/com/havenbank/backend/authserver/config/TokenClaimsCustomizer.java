package com.havenbank.backend.authserver.config;

import com.havenbank.backend.iam.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enriches issued JWTs so the resource server can authorise correctly:
 * <ul>
 *   <li>{@code sub} is set to the <strong>stable user id</strong> (not the email, which can change),
 *       matching what the resource-server controllers resolve from the token subject;</li>
 *   <li>a {@code roles} claim is added (derived from the authenticated authorities), which the
 *       resource server maps to {@code ROLE_*} authorities;</li>
 *   <li>an {@code email} claim is added for convenience;</li>
 *   <li>{@code auth_time} is stamped on the ID token to support later step-up decisions.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserDirectory userDirectory;

    @Value("${app.resource-id:banking-api}")
    private String resourceId;

    @Override
    public void customize(JwtEncodingContext context) {
        String email = context.getPrincipal().getName();

        Set<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(Object::toString)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());

        // The bare (non-ROLE, non-SCOPE) authorities are the user's permissions. Carrying them in the
        // token lets the resource server authorise on permissions (e.g. AUDIT_READ), not just roles.
        Set<String> permissions = context.getPrincipal().getAuthorities().stream()
                .map(Object::toString)
                .filter(a -> !a.startsWith("ROLE_") && !a.startsWith("SCOPE_"))
                .collect(Collectors.toSet());

        userDirectory.findByEmail(email).ifPresent(user ->
                context.getClaims().subject(user.id().toString()));

        context.getClaims().claim("roles", roles);
        context.getClaims().claim("permissions", permissions);
        context.getClaims().claim("email", email);

        // Access tokens carry this API's audience so the resource server can validate `aud`.
        if ("access_token".equals(context.getTokenType().getValue())) {
            context.getClaims().audience(List.of(resourceId));
        }
        // Stamp auth_time on the OIDC ID token to support later step-up decisions.
        if ("id_token".equals(context.getTokenType().getValue())) {
            context.getClaims().claim("auth_time", Instant.now().getEpochSecond());
        }
    }
}
package com.havenbank.backend.iam.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource-server security. The API is stateless and trusts only JWTs validated against the
 * authorization server's JWKS. Registration, verification and password-reset endpoints are public;
 * everything else requires authentication, with fine-grained rules enforced by method security
 * ({@code @PreAuthorize}). URL rules are the first line of defence; ownership checks in the service
 * layer are the second (FR-1.12).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.issuer}")
    private String issuer;

    @Value("${app.resource-id:banking-api}")
    private String resourceId;

    /**
     * Resource-server JWT decoder. Fetches JWKS lazily from the local authorization server (avoiding
     * a startup discovery fetch in this same app), and validates timestamp + issuer, then the logout
     * denylist. One decoder bean serves both the resource server and the OIDC userinfo endpoint.
     */
    @Bean
    public JwtDecoder jwtDecoder(DenylistJwtValidator denylistValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks").build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaults, new AudienceValidator(resourceId), denylistValidator));
        return decoder;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // stateless, token in Authorization header - CSRF N/A
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/register",
                                "/api/v1/register/verify",
                                "/api/v1/register/verify/resend",
                                "/api/v1/password/forgot",
                                "/api/v1/password/reset").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Maps the custom {@code roles} claim to {@code ROLE_*} authorities, the {@code permissions}
     * claim to bare authorities, and OAuth {@code scope} claims to {@code SCOPE_*} - so user roles,
     * user permissions, and client scopes are all enforceable.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopes.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.stream()
                        .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                        .forEach(authorities::add);
            }
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                permissions.stream()
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }
}
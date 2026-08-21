package com.havenbank.backend.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS policy for the browser SPA, which runs on a different origin than the API/token endpoints.
 * Allowed origins are externalised so production can lock them down. Credentials are permitted so the
 * authorization-server session cookie flows during silent re-authentication.
 *
 * <p>Registered only against the endpoints the SPA calls from JavaScript. The server-rendered pages
 * ({@code /login}, {@code /login/otp}) and static assets are reached by top-level navigation, not by
 * fetch, so applying a cross-origin policy to them serves no purpose and can reject a perfectly
 * ordinary page load with "Invalid CORS request".
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id",
                "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);          // REST API
        source.registerCorsConfiguration("/oauth2/token", config);    // PKCE code exchange, refresh
        source.registerCorsConfiguration("/oauth2/jwks", config);
        source.registerCorsConfiguration("/oauth2/revoke", config);
        source.registerCorsConfiguration("/oauth2/introspect", config);
        source.registerCorsConfiguration("/userinfo", config);        // OIDC profile
        source.registerCorsConfiguration("/.well-known/**", config);  // discovery document
        return source;
    }
}
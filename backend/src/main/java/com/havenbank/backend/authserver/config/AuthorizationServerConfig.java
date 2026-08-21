package com.havenbank.backend.authserver.config;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.authserver.security.ReuseDetectingAuthorizationService;
import com.havenbank.backend.iam.security.AppUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Configures the OAuth 2.1 / OIDC protocol endpoints.
 *
 * <p>The client registration and the issuer are defined in {@code application.yaml} under
 * {@code spring.security.oauth2.authorizationserver.*}; Spring Boot builds the
 * {@code RegisteredClientRepository} and {@code AuthorizationServerSettings} from those properties,
 * so this class keeps only what genuinely needs code: the protocol filter chain and a persistent
 * {@link OAuth2AuthorizationService}.
 *
 * <p>The {@link Order} 1 filter chain matches only the authorization-server endpoints
 * ({@code /oauth2/authorize}, {@code /oauth2/token}, {@code /oauth2/jwks}, {@code /userinfo},
 * {@code /.well-known/*}, ...). Unauthenticated browser requests to the authorization endpoint are
 * redirected to the login page (see {@code WebSecurityConfig}).</p>
 */
@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerFilterChain(
            HttpSecurity http,
            RequestCache requestCache,
            SecurityContextRepository securityContextRepository)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                // The OAuth2 protocol endpoints are not cookie-authenticated: /oauth2/token is called
                // by the client with an authorization code and PKCE verifier, so a CSRF token is both
                // impossible for it to obtain and unnecessary. Spring Security 7 protects these by
                // default, which rejects the code exchange with 403 and fails sign-in at the last step.
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServer.getEndpointsMatcher()))
                // Same repository the login/OTP chain writes to. Without this, this chain reads the
                // context through its own default instance and may not see what OtpController saved.
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // Same filtered cache as the interactive chain: this is where the authorization
                // request itself is stored before the user is sent to log in.
                .requestCache(rc -> rc.requestCache(requestCache))
                // Redirect unauthenticated browsers (HTML) to our login page; API clients get 401.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    /**
     * Persist authorizations (authorization codes, access/refresh tokens, rotation state) in Postgres
     * via {@link JdbcOAuth2AuthorizationService} instead of the in-memory default, then wrap that in
     * {@link ReuseDetectingAuthorizationService} to add refresh-token reuse detection with full
     * per-user revocation (RFC 9700). Schema: {@code V8__oauth2_authorization_server.sql} and
     * {@code V9__refresh_token_family.sql}.
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository, AuditService auditService) {
        JdbcOAuth2AuthorizationService store =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        trustAppUserPrincipal(store, registeredClientRepository);
        return new ReuseDetectingAuthorizationService(store, jdbcTemplate, auditService);
    }

    /**
     * The stored authorization's {@code attributes} carry the full {@code Authentication}, whose
     * principal is our {@link AppUserPrincipal}. Spring Security's default {@code JsonMapper} only
     * trusts its own classes for polymorphic deserialization (a defence against deserialization-gadget
     * attacks), so reading a stored row throws {@code InvalidTypeIdException} unless the app's own
     * principal type is explicitly allow-listed here &mdash; deliberately by exact class, not by
     * package, to keep the allow-list minimal.
     */
    private void trustAppUserPrincipal(JdbcOAuth2AuthorizationService store,
                                       RegisteredClientRepository registeredClientRepository) {
        ClassLoader loader = JdbcOAuth2AuthorizationService.class.getClassLoader();
        BasicPolymorphicTypeValidator.Builder validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(AppUserPrincipal.class);
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(loader, validator))
                .build();
        store.setAuthorizationRowMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                registeredClientRepository, jsonMapper));
        store.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper));
    }
}
package com.havenbank.backend.testsupport;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.redis.testcontainers.RedisContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
@AutoConfigureRestTestClient  // ✅ Enables RestTestClient
@AutoConfigureJson            // ✅ Enables ObjectMapper
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("banking")
                    .withUsername("banking")
                    .withPassword("banking");

    private static final RedisContainer REDIS = new RedisContainer("redis:7");

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // RANDOM_PORT normally binds the embedded server to an OS-assigned port (server.port=0),
        // which is fine when nothing needs to know the port in advance. But this app's resource
        // server validates its own tokens by making a real HTTP call back to itself for the JWKS
        // (jwk-set-uri: ${APP_ISSUER}/oauth2/jwks - see application.yaml), and app.issuer defaults
        // to localhost:8080. Under RANDOM_PORT that JWKS fetch goes to the wrong port and every
        // single bearer-token validation fails with "Connection refused" - not just some tokens,
        // all of them, since the resource server can't fetch keys to check ANY signature. Picking
        // the free port ourselves (rather than letting the server pick one at bind time) lets us
        // set server.port and app.issuer to the same, correct value before the context starts.
        int port = org.springframework.test.util.TestSocketUtils.findAvailableTcpPort();
        registry.add("server.port", () -> port);
        registry.add("app.issuer", () -> "http://localhost:" + port);
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected ObjectMapper objectMapper;  // ✅ Works with @AutoConfigureJson

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    protected MockMvc mvc;

    @org.junit.jupiter.api.BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()
        ).build();
        redisConnectionFactory.getConnection().serverCommands().flushAll();
    }

    protected RequestPostProcessor asUser(UUID userId, String email, List<String> roles, List<String> permissions) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)
                .authorities(jwtAuthenticationConverter.convert(jwt).getAuthorities());
    }

    protected RequestPostProcessor asCustomer(UUID userId, String email) {
        return asUser(userId, email, List.of("CUSTOMER"), List.of());
    }

    protected RequestPostProcessor asStaffWithAuditRead(UUID userId, String email) {
        return asUser(userId, email, List.of("STAFF"), List.of("AUDIT_READ"));
    }
}
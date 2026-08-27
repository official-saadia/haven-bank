package com.havenbank.backend.authserver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-1.10: refresh token rotation is enforced, and reusing a consumed refresh token revokes the
 * <em>entire</em> family - the max-safety policy documented in {@code ReuseDetectingAuthorizationService}
 * - not just the reused token itself. A legitimately-issued, never-yet-used refresh token from the
 * same session must also stop working the moment reuse is detected.
 */
class RefreshTokenReuseIntegrationTest extends AbstractIntegrationTest {

    // FR-1.10 (rotation + reuse detection) is only meaningful against a client that can actually
    // receive a refresh token. Spring Authorization Server never issues one to a client registered
    // with client-authentication-methods: none (the "spa" client), so this test authenticates as
    // the test-only confidential client instead - see src/test/resources/application.yaml.
    private static final String CLIENT_ID = "test-confidential";
    private static final String CLIENT_SECRET = "test-confidential-secret";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback";
    private static final String PASSWORD = "a-genuinely-long-passphrase";

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;

    private String email;

    @BeforeEach
    void seedVerifiedCustomer() {
        Role customer = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));
        email = "refresh-reuse+" + UUID.randomUUID() + "@example.com";
        User user = User.builder().email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Refresh Reuse Test").build();
        user.markEmailVerified();
        user.addRole(customer);
        users.save(user);
    }

    @Test
    void reusingAConsumedRefreshTokenRevokesTheWholeFamilyIncludingTheValidRotatedOne() throws Exception {
        JsonNode initialTokens = completeLoginAndExchangeForTokens();
        String refreshToken1 = initialTokens.get("refresh_token").asText();

        // Rotation: the first refresh succeeds and issues a *different* refresh token.
        MvcResult secondResult = refresh(refreshToken1).andExpect(status().isOk()).andReturn();
        JsonNode secondTokens = objectMapper.readTree(secondResult.getResponse().getContentAsString());
        String refreshToken2 = secondTokens.get("refresh_token").asText();
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        // Reusing the now-consumed original is the replay - rejected, and per
        // ReuseDetectingAuthorizationService.detectReuse, this revokes every authorization for the
        // principal, not just the reused token.
        refresh(refreshToken1).andExpect(status().isBadRequest());

        // The actual proof: refreshToken2 was legitimately issued by the rotation above and had
        // never been used - it must now ALSO fail, because the whole family was revoked, not just
        // the specific token that was replayed.
        refresh(refreshToken2).andExpect(status().isBadRequest());
    }

    @Test
    void aSingleRefreshWithoutReuseRotatesCleanlyAndTheNewTokenWorks() throws Exception {
        JsonNode initialTokens = completeLoginAndExchangeForTokens();
        String refreshToken1 = initialTokens.get("refresh_token").asText();

        MvcResult rotatedResult = refresh(refreshToken1).andExpect(status().isOk()).andReturn();
        JsonNode rotated = objectMapper.readTree(rotatedResult.getResponse().getContentAsString());
        String newAccessToken = rotated.get("access_token").asText();

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/oauth2/token")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .httpBasic(CLIENT_ID, CLIENT_SECRET))
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken));
    }

    /**
     * The full Authorization Code + PKCE + OTP flow, identical to {@code LoginFlowIntegrationTest},
     * to obtain a real refresh token bound to a real authorization - reuse detection is meaningless
     * against a token that was never actually issued by the real flow.
     */
    private JsonNode completeLoginAndExchangeForTokens() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String codeVerifier = randomVerifier();
        String codeChallenge = s256(codeVerifier);

        URI authorizeUri = org.springframework.web.util.UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "openid profile accounts.read transfers.write")
                .queryParam("state", UUID.randomUUID().toString())
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUri();
        mvc.perform(get(authorizeUri).session(session)
                        .accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", email).param("password", PASSWORD));

        String otpCode = redis.opsForValue().get("otp:login:" + email.toLowerCase());
        assertThat(otpCode).as("OTP code must have been issued and stored in Redis").isNotNull();

        MvcResult otpVerify = mvc.perform(post("/login/otp").session(session).with(csrf())
                .param("code", otpCode)).andReturn();
        String resumedUrl = otpVerify.getResponse().getRedirectedUrl();

        MvcResult authorizeResumed = mvc.perform(get(URI.create(resumedUrl)).session(session)
                .accept(org.springframework.http.MediaType.TEXT_HTML)).andReturn();
        String callbackUrl = authorizeResumed.getResponse().getRedirectedUrl();
        String authorizationCode = extractQueryParam(callbackUrl, "code");

        MvcResult tokenResponse = mvc.perform(post("/oauth2/token")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "authorization_code")
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(tokenResponse.getResponse().getContentAsString());
    }

    private static String randomVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String s256(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static String extractQueryParam(String url, String name) {
        for (String param : URI.create(url).getRawQuery().split("&")) {
            String[] pair = param.split("=", 2);
            if (pair[0].equals(name)) {
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
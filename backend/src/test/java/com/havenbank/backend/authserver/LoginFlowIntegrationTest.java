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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
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
 * Walks the real Authorization Code + PKCE flow end to end: an unauthenticated
 * {@code /oauth2/authorize} request is redirected to login, the password step hands off to email
 * OTP rather than completing authentication (FR-1.4/1.5/1.8), OTP verification resumes the original
 * authorization request, and the resulting code is exchanged for a real, signed access token that
 * a protected API endpoint actually accepts.
 *
 * <p>This is the flow every unit test in this codebase deliberately stubs around - it needs a real
 * session, real cookies, and a real Redis-backed OTP code, which only an integration test can give.
 */
class LoginFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String CLIENT_ID = "spa";
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
        email = "login-flow+" + UUID.randomUUID() + "@example.com";
        User user = User.builder().email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Login Flow Test").build();
        user.markEmailVerified();
        user.addRole(customer);
        users.save(user);
    }

    @Test
    void completesTheFullAuthorizationCodePkceFlowAndTheTokenWorksAgainstARealEndpoint() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // PKCE: a random verifier, and the S256 challenge derived from it - exactly what the SPA's
        // oidc-client-ts would generate before the first redirect.
        String codeVerifier = randomVerifier();
        String codeChallenge = s256(codeVerifier);
        String state = UUID.randomUUID().toString();

        // Step 1: unauthenticated authorization request -> redirected to /login, and the request
        // itself is cached (WebSecurityConfig's shared RequestCache) to be resumed after OTP.
        // NB: built as a single URI with the query string baked in, not via .param(...) - for a
        // GET request MockMvc's .param() only populates the servlet parameter map, not
        // request.getQueryString(), which is what Spring Authorization Server's authorization
        // request converter actually reads. With .param() alone every param looks missing to it.
        URI authorizeUri = org.springframework.web.util.UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "openid profile accounts.read transfers.write")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUri();
        MvcResult authorizeAttempt = mvc.perform(get(authorizeUri)
                        .session(session)
                        .accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(authorizeAttempt.getResponse().getRedirectedUrl()).contains("/login");

        // Step 2: password step. Correct credentials do NOT complete authentication - they hand off
        // to OTP (MfaAuthenticationSuccessHandler clears the SecurityContext it just built).
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login/otp"));

        // Step 3: the real OTP code, read from the same Redis the app itself just wrote it to -
        // OtpService.issue() returns it only to MfaAuthenticationSuccessHandler, which emails it;
        // there is no API that hands it back, by design (FR-1.8b).
        String otpCode = redis.opsForValue().get("otp:login:" + email.toLowerCase());
        assertThat(otpCode).as("OTP code must have been issued and stored in Redis").isNotNull();

        // Step 4: submitting it resumes the originally cached /oauth2/authorize request.
        MvcResult otpVerify = mvc.perform(post("/login/otp").session(session).with(csrf())
                        .param("code", otpCode))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String resumedUrl = otpVerify.getResponse().getRedirectedUrl();
        assertThat(resumedUrl).contains("/oauth2/authorize");

        // Step 5: now authenticated, the resumed request is auto-approved (require-authorization-
        // consent: false for this first-party client) and redirects to the SPA with a real code.
        MvcResult authorizeResumed = mvc.perform(get(URI.create(resumedUrl)).session(session)
                        .accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String callbackUrl = authorizeResumed.getResponse().getRedirectedUrl();
        assertThat(callbackUrl).startsWith(REDIRECT_URI);
        String authorizationCode = extractQueryParam(callbackUrl, "code");
        assertThat(authorizationCode).as("authorization code in the callback redirect").isNotNull();

        // Step 6: the real token exchange. "spa" is a public client (client-authentication-methods:
        // none), so the verifier is the only proof required - no client secret exists to send.
        MvcResult tokenResponse = mvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenJson = objectMapper.readTree(tokenResponse.getResponse().getContentAsString());
        String accessToken = tokenJson.get("access_token").asText();
        assertThat(tokenJson.has("refresh_token")).isTrue(); //@todo check is true
        assertThat(tokenJson.has("id_token")).isTrue();

        // Step 7: the real proof - this token, issued by the flow above, is accepted by an actual
        // protected resource-server endpoint.
        mvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void wrongOtpCodeDoesNotCompleteAuthentication() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String codeVerifier = randomVerifier();
        String codeChallenge = s256(codeVerifier);

        URI authorizeUri = org.springframework.web.util.UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "openid accounts.read")
                .queryParam("state", UUID.randomUUID().toString())
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUri();
        mvc.perform(get(authorizeUri).session(session)
                .accept(org.springframework.http.MediaType.TEXT_HTML));

        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", email).param("password", PASSWORD));

        // A wrong code must not advance the flow to /oauth2/authorize.
        mvc.perform(post("/login/otp").session(session).with(csrf()).param("code", "000000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login/otp?error"));

        // And the session must still be unauthenticated - a protected endpoint must reject it.
        mvc.perform(get("/api/v1/accounts").session(session))
                .andExpect(status().isUnauthorized());
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
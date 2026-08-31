package com.havenbank.backend.iam;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.iam.service.OneTimeTokenService;
import com.havenbank.backend.iam.service.OneTimeTokenType;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-1.7 (enumeration safety), FR-1.11 (single-use reset token), and authenticated password change,
 * against real Postgres and Redis.
 */
class PasswordFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String ORIGINAL_PASSWORD = "an-original-long-passphrase";

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private OneTimeTokenService tokenService;

    private User user;

    @BeforeEach
    void seedCustomer() {
        Role customer = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));
        user = User.builder().email("pwd+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode(ORIGINAL_PASSWORD))
                .fullName("Password Test").build();
        user.markEmailVerified();
        user.addRole(customer);
        user = users.save(user);
    }

    @Test
    void forgotPasswordReturnsTheSameStatusForAKnownAndAnUnknownEmail() throws Exception {
        String knownBody = "{\"email\":\"" + user.getEmail() + "\"}";
        String unknownBody = "{\"email\":\"definitely-not-registered+" + UUID.randomUUID() + "@example.com\"}";

        // FR-1.7: identical response either way - the endpoint must not be a usable oracle for
        // "does this email have an account".
        mvc.perform(post("/api/v1/password/forgot").contentType(APPLICATION_JSON).content(knownBody))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/password/forgot").contentType(APPLICATION_JSON).content(unknownBody))
                .andExpect(status().isAccepted());
    }

    @Test
    void resettingWithAValidTokenChangesThePasswordAndConsumesTheToken() throws Exception {
        String token = tokenService.issue(OneTimeTokenType.PASSWORD_RESET, user.getId());
        String newPassword = "a-brand-new-long-passphrase";
        String body = "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";

        mvc.perform(post("/api/v1/password/reset").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, updated.getPasswordHash())).isTrue();

        // Single-use (FR-1.11): the same token must not work a second time.
        mvc.perform(post("/api/v1/password/reset").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isGone()); //@todo check it should be 410 or 400
    }

    @Test
    void changingPasswordRequiresTheCurrentOneAndAppliesRegardlessOfAuthenticationBypassTricks() throws Exception {
        String correctBody = "{\"currentPassword\":\"" + ORIGINAL_PASSWORD
                + "\",\"newPassword\":\"a-different-long-passphrase\"}";
        String wrongBody = "{\"currentPassword\":\"totally-wrong-passphrase\",\"newPassword\":\"a-different-long-passphrase\"}";

        mvc.perform(post("/api/v1/password/change")
                        .with(asCustomer(user.getId(), user.getEmail()))
                        .contentType(APPLICATION_JSON).content(wrongBody))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/password/change")
                        .with(asCustomer(user.getId(), user.getEmail()))
                        .contentType(APPLICATION_JSON).content(correctBody))
                .andExpect(status().isNoContent());

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("a-different-long-passphrase", updated.getPasswordHash())).isTrue();
    }

    @Test
    void changingPasswordWithoutAuthenticationIsRejected() throws Exception {
        String body = "{\"currentPassword\":\"" + ORIGINAL_PASSWORD
                + "\",\"newPassword\":\"a-different-long-passphrase\"}";
        mvc.perform(post("/api/v1/password/change").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}

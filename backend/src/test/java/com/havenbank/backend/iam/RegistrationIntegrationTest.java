package com.havenbank.backend.iam;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-1.1, FR-1.7: registration and its enumeration-safety guarantee, against real Postgres.
 */
class RegistrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedCustomerRole() {
        roles.findByName("CUSTOMER").orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));
    }

    @Test
    void registeringWithANewEmailIsAccepted() throws Exception {
        String body = """
                {"email":"new.customer@example.com","password":"a-genuinely-long-passphrase","fullName":"New Customer"}
                """;

        mvc.perform(post("/api/v1/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        assertThat(users.findByEmailIgnoreCase("new.customer@example.com")).isPresent();
    }

    @Test
    void registeringWithAnAlreadyRegisteredEmailReturnsTheSameStatusAsANewOne() throws Exception {
        // FR-1.7: an existing, already-active account must not produce a different response from
        // a brand-new registration - otherwise the endpoint becomes a user-enumeration oracle.
        User existing = User.builder()
                .email("already.registered@example.com")
                .passwordHash(passwordEncoder.encode("some-other-passphrase"))
                .fullName("Existing Customer")
                .build();
        existing.markEmailVerified();
        users.save(existing);

        String body = """
                {"email":"already.registered@example.com","password":"a-different-long-passphrase","fullName":"Attempted Duplicate"}
                """;

        // Same 202, same shape, as the new-email case above - proven by asserting the identical
        // status code against the identical endpoint, not just documented in a comment.
        mvc.perform(post("/api/v1/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        // And critically: the existing account must be untouched, not silently overwritten.
        User unchanged = users.findByEmailIgnoreCase("already.registered@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("some-other-passphrase", unchanged.getPasswordHash())).isTrue();
    }

    @Test
    void verifyingWithAnInvalidTokenIsRejected() throws Exception {
        String body = """
                {"token":"not-a-real-token"}
                """;

        mvc.perform(post("/api/v1/register/verify").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}

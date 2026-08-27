package com.havenbank.backend.misc;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Lighter coverage for the remaining endpoints: basic authentication/ownership gating rather than
 * each service's full business logic (already unit-tested elsewhere - see the README's Testing
 * section, e.g. {@code StepUpServiceTest}).
 */
class MiscEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private User customer;

    @BeforeEach
    void seedCustomer() {
        Role role = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));
        customer = User.builder().email("misc+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName("Misc Endpoints Test").build();
        customer.markEmailVerified();
        customer.addRole(role);
        customer = users.save(customer);
    }

    // --- MeController ---------------------------------------------------------------------

    @Test
    void authenticatedUserCanReadTheirOwnProfile() throws Exception {
        mvc.perform(get("/api/v1/me").with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.getEmail()));
    }

    @Test
    void unauthenticatedProfileRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    // --- LogoutController ------------------------------------------------------------------

    @Test
    void authenticatedLogoutSucceeds() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isNoContent());
        // jti-denylist enforcement on a subsequent request is already covered at unit level by
        // DenylistJwtValidatorTest; this only proves the endpoint itself completes for a real user.
    }

    @Test
    void unauthenticatedLogoutIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }

    // --- StepUpController (FR-3.9) ----------------------------------------------------------

    @Test
    void authenticatedUserCanRequestAStepUpChallenge() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/challenge").with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isAccepted());
    }

    @Test
    void verifyingAnObviouslyWrongStepUpCodeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/challenge").with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isAccepted());

        String body = """
                {"code":"000000"}
                """;
        mvc.perform(post("/api/v1/auth/otp/verify")
                        .with(asCustomer(customer.getId(), customer.getEmail()))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unauthenticatedStepUpChallengeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/challenge")).andExpect(status().isUnauthorized());
    }

    // --- TransactionController --------------------------------------------------------------

    @Test
    void aliceCannotReadBobsTransactionHistory() throws Exception {
        User bob = save("bob");
        UUID bobsAccountId = openAccount(bob);

        mvc.perform(get("/api/v1/accounts/{id}/transactions", bobsAccountId)
                        .with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanReadTheirOwnTransactionHistory() throws Exception {
        UUID accountId = openAccount(customer);

        mvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                        .with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void statementExportReturnsCsv() throws Exception {
        UUID accountId = openAccount(customer);

        mvc.perform(get("/api/v1/accounts/{id}/statement", accountId)
                        .with(asCustomer(customer.getId(), customer.getEmail()))
                        .param("from", "2020-01-01T00:00:00Z")
                        .param("to", "2030-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    private User save(String name) {
        Role role = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));
        User user = User.builder().email(name + "+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName(name.substring(0, 1).toUpperCase() + name.substring(1)).build();
        user.markEmailVerified();
        user.addRole(role);
        return users.save(user);
    }

    private UUID openAccount(User owner) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON).content("{\"type\":\"CHECKING\",\"currency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
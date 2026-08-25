package com.havenbank.backend.money.service;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-2.3 (ownership / IDOR) and FR-3.7 (idempotency), on account opening, against real Postgres.
 */
class AccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private User alice;
    private User bob;

    @BeforeEach
    void seedTwoCustomers() {
        Role customer = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));

        alice = User.builder().email("alice+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName("Alice Anderson").build();
        alice.markEmailVerified();
        alice.addRole(customer);
        alice = users.save(alice);

        bob = User.builder().email("bob+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName("Bob Baker").build();
        bob.markEmailVerified();
        bob.addRole(customer);
        bob = users.save(bob);
    }

    @Test
    void aliceCannotReadBobsAccountById() throws Exception {
        UUID bobsAccountId = openAccount(bob, "CHECKING", "GBP");

        // FR-2.3: not just "denied" - it must be indistinguishable from "does not exist" (404,
        // not 403), so the response itself cannot confirm the account is real.
        mvc.perform(get("/api/v1/accounts/{id}", bobsAccountId).with(asCustomer(alice.getId(), alice.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aliceCanReadHerOwnAccount() throws Exception {
        UUID aliceAccountId = openAccount(alice, "CHECKING", "GBP");

        mvc.perform(get("/api/v1/accounts/{id}", aliceAccountId).with(asCustomer(alice.getId(), alice.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void replayingTheIdempotencyKeyOnAccountOpenReturnsTheOriginalAccountNotASecondOne() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {"type":"SAVINGS","currency":"GBP"}
                """;

        MvcResult first = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(alice.getId(), alice.getEmail()))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult replay = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(alice.getId(), alice.getEmail()))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id");
        JsonNode replayId = objectMapper.readTree(replay.getResponse().getContentAsString()).get("id");
        assertThat(replayId.asText()).isEqualTo(firstId.asText());

        // The real proof: exactly one savings account exists for Alice, not two.
        long savingsAccounts = accountsOfType(alice.getId(), "SAVINGS");
        assertThat(savingsAccounts).isEqualTo(1);
    }

    private UUID openAccount(User owner, String type, String currency) throws Exception {
        String body = "{\"type\":\"" + type + "\",\"currency\":\"" + currency + "\"}";
        MvcResult result = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private long accountsOfType(UUID ownerId, String type) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/accounts").with(asCustomer(ownerId, ""))).andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        long count = 0;
        for (JsonNode account : list) {
            if (account.get("type").asText().equals(type)) count++;
        }
        return count;
    }
}

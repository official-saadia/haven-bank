package com.havenbank.backend.money;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-1.12 / the beneficiary-service's account-enumeration deliberately not checked at save time
 * (see {@code BeneficiaryRequest}'s javadoc) - a payee is scoped to its owner regardless.
 */
class BeneficiaryIntegrationTest extends AbstractIntegrationTest {

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
        alice = save("alice", customer);
        bob = save("bob", customer);
    }

    @Test
    void aliceCannotUpdateOrDeleteBobsBeneficiary() throws Exception {
        UUID bobsBeneficiaryId = addBeneficiary(bob, "Bob's Landlord", "GB29NWBK60161331926819");

        String updateBody = """
                {"name":"Renamed","accountNumber":"GB29NWBK60161331926819"}
                """;
        mvc.perform(put("/api/v1/beneficiaries/{id}", bobsBeneficiaryId)
                        .with(asCustomer(alice.getId(), alice.getEmail()))
                        .contentType(APPLICATION_JSON).content(updateBody))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/beneficiaries/{id}", bobsBeneficiaryId)
                        .with(asCustomer(alice.getId(), alice.getEmail())))
                .andExpect(status().isNotFound());

        // And it must still exist for Bob - the rejected attempt above did nothing.
        MvcResult bobsList = mvc.perform(get("/api/v1/beneficiaries")
                        .with(asCustomer(bob.getId(), bob.getEmail())))
                .andReturn();
        JsonNode list = objectMapper.readTree(bobsList.getResponse().getContentAsString());
        assertThat(list).anyMatch(b -> b.get("id").asText().equals(bobsBeneficiaryId.toString()));
    }

    @Test
    void alicesBeneficiaryListNeverIncludesBobs() throws Exception {
        addBeneficiary(bob, "Bob's Gym", "GB94BARC20201530093459");
        addBeneficiary(alice, "Alice's Landlord", "GB33BUKB20201555555555");

        MvcResult result = mvc.perform(get("/api/v1/beneficiaries")
                        .with(asCustomer(alice.getId(), alice.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("name").asText()).isEqualTo("Alice's Landlord");
    }

    @Test
    void savingABeneficiaryDoesNotRequireTheAccountNumberToExist() throws Exception {
        // Deliberate per BeneficiaryRequest's javadoc: existence is checked only at transfer time,
        // where it is already rate-limited and audited - saving a payee must not become an
        // account-enumeration oracle.
        String body = """
                {"name":"Nonexistent Payee","accountNumber":"ZZ00NOSUCHACCT12345"}
                """;
        mvc.perform(post("/api/v1/beneficiaries")
                        .with(asCustomer(alice.getId(), alice.getEmail()))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private User save(String name, Role role) {
        User user = User.builder().email(name + "+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName(name.substring(0, 1).toUpperCase() + name.substring(1)).build();
        user.markEmailVerified();
        user.addRole(role);
        return users.save(user);
    }

    private UUID addBeneficiary(User owner, String name, String accountNumber) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"accountNumber\":\"" + accountNumber + "\"}";
        MvcResult result = mvc.perform(post("/api/v1/beneficiaries")
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
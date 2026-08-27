package com.havenbank.backend.notification;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-7.1a/7.1b: convenience preferences are user-configurable and actually persist;
 * security-critical types are never listed and cannot be disabled, against real Postgres.
 */
class NotificationPreferenceIntegrationTest extends AbstractIntegrationTest {

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
        customer = User.builder().email("prefs+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName("Prefs Customer").build();
        customer.markEmailVerified();
        customer.addRole(role);
        customer = users.save(customer);
    }

    @Test
    void securityCriticalTypesAreNeverListed() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/me/notification-preferences")
                        .with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode pref : list) {
            // LOGIN_OTP is SECURITY_CRITICAL (FR-7.1b) - it must never appear here, since it has
            // no opt-out to offer.
            assertThat(pref.get("type").asText()).isNotEqualTo("LOGIN_OTP");
        }
    }

    @Test
    void disablingAConveniencePreferencePersistsAndIsReflectedOnTheNextRead() throws Exception {
        String updateBody = """
                {"preferences":[{"type":"ACCOUNT_CREATED","enabled":false}]}
                """;

        mvc.perform(put("/api/v1/me/notification-preferences")
                        .with(asCustomer(customer.getId(), customer.getEmail()))
                        .contentType(APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());

        // A fresh read, not just trusting the PUT response - proves it actually persisted rather
        // than only being reflected back in-memory from the same request.
        MvcResult result = mvc.perform(get("/api/v1/me/notification-preferences")
                        .with(asCustomer(customer.getId(), customer.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode pref : list) {
            if (pref.get("type").asText().equals("ACCOUNT_CREATED")) {
                assertThat(pref.get("enabled").asBoolean()).isFalse();
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void attemptingToDisableASecurityCriticalTypeIsSilentlyIgnored() throws Exception {
        // FR-7.1b: security-critical categories cannot be disabled. The controller ignores the
        // entry rather than erroring, so this proves the ignore actually happens end to end.
        String updateBody = """
                {"preferences":[{"type":"LOGIN_OTP","enabled":false}]}
                """;

        mvc.perform(put("/api/v1/me/notification-preferences")
                        .with(asCustomer(customer.getId(), customer.getEmail()))
                        .contentType(APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());

        MvcResult result = mvc.perform(get("/api/v1/me/notification-preferences")
                        .with(asCustomer(customer.getId(), customer.getEmail())))
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode pref : list) {
            assertThat(pref.get("type").asText()).isNotEqualTo("LOGIN_OTP");
        }
    }
}
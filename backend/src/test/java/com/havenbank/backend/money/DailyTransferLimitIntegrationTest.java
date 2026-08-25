package com.havenbank.backend.money;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.domain.PolicyKey;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.money.repository.PolicyRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-3.11: a rolling daily transfer limit, enforced across all of a customer's outbound money
 * movements, cumulatively - not just checked per-transfer against a fixed amount. The requirements
 * doc's design note is explicit that the step-up threshold (FR-3.9) must sit below the daily limit,
 * so this seeds both far enough apart that step-up never fires and only the daily-limit path is
 * being exercised.
 */
class DailyTransferLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LedgerEntryRepository ledger;
    @Autowired
    private PolicyRepository policies;

    private User owner;
    private User recipient;
    private UUID sourceAccountId;
    private UUID recipientAccountId;

    @BeforeEach
    void seedFundedCustomerWithATightDailyLimit() throws Exception {
        Role customer = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));

        owner = save("limit-owner", customer);
        recipient = save("limit-recipient", customer);
        sourceAccountId = openAccount(owner, "10000.00");
        recipientAccountId = openAccount(recipient, "0.00");

        // Daily limit tight enough to trip after two transfers; step-up threshold kept well above
        // it so this test exercises only enforceDailyLimit, not enforceStepUp (FR-3.9's threshold
        // must sit below the daily limit in production - deliberately inverted here for isolation).
        seedPolicy(PolicyKey.DAILY_LIMIT, "150.00");
        seedPolicy(PolicyKey.STEP_UP_THRESHOLD, "100000.00");
    }

    @Test
    void aTransferThatWouldExceedTheDailyLimitIsRejectedWhileEarlierOnesStandAlreadyPosted() throws Exception {
        // First transfer: 100.00, well within the 150.00 daily limit.
        transfer(new BigDecimal("100.00")).andExpect(status().isCreated());
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualByComparingTo("9900.00");

        // Second transfer: only 50.00 more, but 100 + 50 = 150.00 is not itself over the limit -
        // this one should still succeed, proving the check is cumulative, not "is this one
        // transfer over 150 on its own".
        transfer(new BigDecimal("50.00")).andExpect(status().isCreated());
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualByComparingTo("9850.00");

        // Third transfer: even 0.01 more now pushes the day's total to 150.01, over the 150.00
        // limit - this is the actual FR-3.11 check firing.
        transfer(new BigDecimal("0.01")).andExpect(status().isUnprocessableEntity());

        // And critically: rejecting the third transfer must not have touched the balance at all -
        // the first two, already posted, must stand exactly as they were.
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualByComparingTo("9850.00");
    }

    @Test
    void aSingleTransferWellUnderTheDailyLimitSucceeds() throws Exception {
        transfer(new BigDecimal("10.00")).andExpect(status().isCreated());
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualByComparingTo("9990.00");
    }

    private org.springframework.test.web.servlet.ResultActions transfer(BigDecimal amount) throws Exception {
        String body = "{\"sourceAccountId\":\"" + sourceAccountId + "\",\"destinationAccountId\":\""
                + recipientAccountId + "\",\"amount\":" + amount + "}";
        return mvc.perform(post("/api/v1/transfers")
                .with(asCustomer(owner.getId(), owner.getEmail()))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(APPLICATION_JSON).content(body));
    }

    private void seedPolicy(PolicyKey key, String value) {
        policies.save(Policy.builder().policyKey(key).scope("GLOBAL")
                .value(new BigDecimal(value)).effectiveFrom(Instant.now().minusSeconds(60)).build());
    }

    private User save(String name, Role role) {
        User user = User.builder().email(name + "+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName(name).build();
        user.markEmailVerified();
        user.addRole(role);
        return users.save(user);
    }

    private UUID openAccount(User owner, String initialDeposit) throws Exception {
        MvcResult opened = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON).content("{\"type\":\"CHECKING\",\"currency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(
                objectMapper.readTree(opened.getResponse().getContentAsString()).get("id").asText());

        if (new BigDecimal(initialDeposit).signum() > 0) {
            mvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                            .with(asCustomer(owner.getId(), owner.getEmail()))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(APPLICATION_JSON).content("{\"amount\": " + initialDeposit + "}"))
                    .andExpect(status().isCreated());
        }
        return accountId;
    }
}

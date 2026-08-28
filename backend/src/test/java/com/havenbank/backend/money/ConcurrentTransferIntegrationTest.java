package com.havenbank.backend.money;

import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-3.8 under the specific pattern that actually risks deadlock rather than just a lost update:
 * two customers transferring to each other at the same moment.
 * {@code MoneyMovementService.transfer()} always locks source-then-destination via
 * {@code AccountRepository.findByIdForUpdate}, in that fixed order, on every call - so Alice→Bob and
 * Bob→Alice firing at the same instant lock in opposite order (A holds Alice, waits for Bob; B holds
 * Bob, waits for Alice). This proves the outcome is safe under that condition, not that it is fast:
 * Postgres detects and aborts one side rather than corrupting data, and whichever side does complete
 * leaves both balances exactly correct.
 */
@AutoConfigureRestTestClient // ✅ Enable RestTestClient for Spring Boot 4
class ConcurrentTransferIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ✅ ObjectMapper from parent class should work with @AutoConfigureJson
    // If still having issues, uncomment:
    // @Autowired
    // protected ObjectMapper objectMapper;

    @Autowired
    private LedgerEntryRepository ledger;

    @Autowired
    private com.havenbank.backend.money.repository.PolicyRepository policies;

    // ✅ Add RestTestClient autowiring
    @Autowired
    private RestTestClient restTestClient; // ✅ This is the correct bean name

    private User alice;
    private User bob;
    private UUID aliceAccountId;
    private UUID bobAccountId;
    @Value("${app.issuer:http://localhost:8080}")
    private String issuerUri;

    @BeforeEach
    void seedTwoFundedCustomers() throws Exception {
        Role customer = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));

        alice = save("alice", customer);
        bob = save("bob", customer);

        aliceAccountId = openAccount(alice, "500.00");
        bobAccountId = openAccount(bob, "500.00");

        // transfer() unconditionally consults both policies (enforceDailyLimit, enforceStepUp) -
        // with no seeded row, PolicyService throws IllegalStateException before either transfer in
        // this test could ever run. Seeded generously high so neither threshold interferes with
        // what this test is actually about (lock ordering), matching how AdminFeePolicyController
        // would create the first version of each in production.
        seedPolicy(com.havenbank.backend.money.domain.PolicyKey.DAILY_LIMIT, "100000.00");
        seedPolicy(com.havenbank.backend.money.domain.PolicyKey.STEP_UP_THRESHOLD, "100000.00");
    }

    private void seedPolicy(com.havenbank.backend.money.domain.PolicyKey key, String value) {
        policies.save(com.havenbank.backend.money.domain.Policy.builder()
                .policyKey(key).scope("GLOBAL")
                .value(new BigDecimal(value)).effectiveFrom(Instant.now().minusSeconds(60)).build());
    }

    @Test
    void simultaneousOppositeDirectionTransfersLeaveBothBalancesExactlyCorrect() throws Exception {
        BigDecimal aliceToBob = new BigDecimal("100.00");
        BigDecimal bobToAlice = new BigDecimal("50.00");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startingGun = new CountDownLatch(1);
        AtomicBoolean aliceTransferSucceeded = new AtomicBoolean(false);
        AtomicBoolean bobTransferSucceeded = new AtomicBoolean(false);

        var aliceTask = pool.submit(() -> {
            try {
                startingGun.await();
                aliceTransferSucceeded.set(transferReal(alice, aliceAccountId, bobAccountId, aliceToBob));
            } catch (Exception ignored) {
                // left false - a deadlock loser throwing here is an acceptable outcome, checked below
            }
        });
        var bobTask = pool.submit(() -> {
            try {
                startingGun.await();
                bobTransferSucceeded.set(transferReal(bob, bobAccountId, aliceAccountId, bobToAlice));
            } catch (Exception ignored) {
                // left false
            }
        });

        startingGun.countDown();
        aliceTask.get(30, TimeUnit.SECONDS);
        bobTask.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // The actual proof: whichever combination of the two transfers went through, the ledger -
        // derived, not a stored field - reflects exactly that combination. No lost update, no
        // double application, no silently corrupted balance from the opposite-order lock attempt.
        BigDecimal expectedAlice = new BigDecimal("500.00");
        BigDecimal expectedBob = new BigDecimal("500.00");
        if (aliceTransferSucceeded.get()) {
            expectedAlice = expectedAlice.subtract(aliceToBob);
            expectedBob = expectedBob.add(aliceToBob);
        }
        if (bobTransferSucceeded.get()) {
            expectedBob = expectedBob.subtract(bobToAlice);
            expectedAlice = expectedAlice.add(bobToAlice);
        }

        assertThat(ledger.balanceOf(aliceAccountId)).isEqualByComparingTo(expectedAlice);
        assertThat(ledger.balanceOf(bobAccountId)).isEqualByComparingTo(expectedBob);

        // At least one side must have actually gone through - a real deadlock aborts the loser and
        // lets the winner commit; both failing would mean something worse than a lock conflict.
        assertThat(aliceTransferSucceeded.get() || bobTransferSucceeded.get()).isTrue();
    }

    private boolean transferReal(User from, UUID fromAccountId, UUID toAccountId, BigDecimal amount)
            throws Exception {

        // ✅ Use RestTestClient's fluent API (Spring Boot 4 style)
        // returnResult(...) (not expectStatus()) deliberately avoids asserting here - a deadlock
        // loser throwing/getting a non-2xx is an acceptable outcome, checked by the caller.
        var result = restTestClient.post()
                .uri("/api/v1/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + testJwt(from))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"sourceAccountId\":\"" + fromAccountId + "\",\"destinationAccountId\":\""
                        + toAccountId + "\",\"amount\":" + amount + "}")
                .exchange()
                .returnResult(String.class);

        return result.getStatus().is2xxSuccessful();
    }

    private User save(String name, Role role) {
        User user = User.builder()
                .email(name + "+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName(name.substring(0, 1).toUpperCase() + name.substring(1))
                .build();
        user.markEmailVerified();
        user.addRole(role);
        return users.save(user);
    }

    private UUID openAccount(User owner, String initialDeposit) throws Exception {
        MvcResult opened = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content("{\"type\":\"CHECKING\",\"currency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID accountId = UUID.fromString(
                objectMapper.readTree(opened.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\": " + initialDeposit + "}"))
                .andExpect(status().isCreated());

        return accountId;
    }

    /**
     * A real, signed JWT for {@link RestTestClient} calls -
     * see {@code MoneyMovementIntegrationTest} for why {@code asCustomer(...)}'s
     * decoding-bypass approach cannot be used for genuine concurrent HTTP threads.
     */
    private String testJwt(User user) {
        com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource =
                webApplicationContext.getBean(com.nimbusds.jose.jwk.source.JWKSource.class);
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(jwkSource);
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(issuerUri)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of("CUSTOMER"))
                .claim("permissions", List.of())
                .audience(List.of("banking-api"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader.with(
                org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();
        return encoder.encode(
                        org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
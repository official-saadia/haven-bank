package com.havenbank.backend.money.service;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import tools.jackson.databind.JsonNode; // ✅ Jackson 3 import
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.resttestclient.RestTestClient;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper; // ✅ Jackson 3 import

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureRestTestClient
class MoneyMovementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper; // ✅ Jackson 3 ObjectMapper

    @Autowired
    private LedgerEntryRepository ledger;

    @Autowired
    private RestTestClient restTestClient; // ✅ Correct import

    private User owner;

    @BeforeEach
    void seedCustomer() {
        Role customer = roles.findByName("CUSTOMER")
                .orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").build()));
        owner = User.builder()
                .email("owner+" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant-for-this-test"))
                .fullName("Owner Owenson")
                .build();
        owner.markEmailVerified();
        owner.addRole(customer);
        owner = users.save(owner);
    }

    @Test
    void replayingTheIdempotencyKeyOnADepositIsRejectedAndDoesNotDoubleCredit() throws Exception {
        UUID accountId = openAccount("CHECKING", "GBP");
        String idempotencyKey = UUID.randomUUID().toString();
        String depositBody = """
                {"amount": 100.00}
                """;

        mvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(depositBody))
                .andExpect(status().isCreated());

        // Same key, same operation, replayed - MoneyMovementService.guardIdempotency rejects it
        // outright (409) rather than re-executing it.
        mvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(depositBody))
                .andExpect(status().isConflict());

        // The real proof, not just the status code: the balance reflects one deposit, not two.
        assertThat(ledger.balanceOf(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void concurrentDepositsAgainstTheSameAccountDoNotLoseUpdates() throws Exception {
        UUID accountId = openAccount("CHECKING", "GBP");
        int concurrentDeposits = 15; // below SENSITIVE's 20/min so this test's correctness doesn't
        // depend on the current rate-limit configuration
        BigDecimal amountEach = new BigDecimal("10.00");

        // Real threads hitting the real embedded server via RestTestClient; not MockMvc, so this
        // exercises genuine concurrent HTTP connections and genuine concurrent transactions - the
        // scenario FR-3.8 and AccountRepository.findByIdForUpdate's pessimistic lock exist for.
        String token = testJwt();
        ExecutorService pool = Executors.newFixedThreadPool(concurrentDeposits);
        CountDownLatch startingGun = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < concurrentDeposits; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startingGun.await();

                    // ✅ Use RestTestClient fluent API (Spring Boot 4 style)
                    var response = restTestClient.post()
                            .uri("/api/v1/accounts/{id}/deposit", accountId)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"amount\": " + amountEach + "}")
                            .exchange();

                    if (response.getStatus().is2xxSuccessful()) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // counted via successes; a thread that fails simply doesn't increment it
                }
            }));
        }

        startingGun.countDown();
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(concurrentDeposits);
        // The actual assertion that matters: if the pessimistic lock had failed to serialise these,
        // some deposits would be lost and the balance would fall short of the full sum.
        assertThat(ledger.balanceOf(accountId))
                .isEqualByComparingTo(amountEach.multiply(BigDecimal.valueOf(concurrentDeposits)));
    }

    @Test
    void withdrawingMoreThanTheBalanceIsRejected() throws Exception {
        UUID accountId = openAccount("CHECKING", "GBP");
        String body = """
                {"amount": 50.00}
                """;

        mvc.perform(post("/api/v1/accounts/{id}/withdraw", accountId)
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());

        assertThat(ledger.balanceOf(accountId)).isEqualByComparingTo("0.00");
    }

    private UUID openAccount(String type, String currency) throws Exception {
        String body = "{\"type\":\"" + type + "\",\"currency\":\"" + currency + "\"}";
        MvcResult result = mvc.perform(post("/api/v1/accounts")
                        .with(asCustomer(owner.getId(), owner.getEmail()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    /**
     * A real, signed JWT for {@link RestTestClient} calls, which go through the actual
     * {@code NimbusJwtDecoder} - unlike {@code mvc}'s {@code asCustomer(...)}, which bypasses
     * decoding entirely via {@code SecurityMockMvcRequestPostProcessors}. Built from the app's own
     * {@code JWKSource<SecurityContext>} bean ({@code JwkConfig.jwkSource()}), which is the actual
     * signing key the resource server validates against - no separate {@code JwtEncoder} bean
     * exists in this app, so one is constructed here rather than autowired.
     */
    private String testJwt() {
        com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource =
                webApplicationContext.getBean(com.nimbusds.jose.jwk.source.JWKSource.class);
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(jwkSource);
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .subject(owner.getId().toString())
                .claim("email", owner.getEmail())
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
package com.havenbank.backend.shared.ratelimit;

import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves rate limiting works end to end - real Redis, real {@code RateLimitFilter}, positioned
 * ahead of Spring Security in the real filter chain - rather than only the mocked-Redis unit
 * coverage in {@link RateLimiterTest}.
 */
// src/test/resources/application.yaml raises CRITICAL to 25/min so other integration tests (login,
// OTP, registration flows) don't get accidentally throttled mid-test by production's tight 5/min.
// This test's whole point is exercising that real 5/min ceiling, so it overrides back down, scoped
// to only this class - Spring Boot test context caching keys off the full property set, so this
// gets its own cached context rather than disturbing the 25/min one every other integration test
// shares.
@TestPropertySource(properties = "app.ratelimit.tiers.critical.limit=5")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void theSixthCriticalRequestInAMinuteIsThrottled() throws Exception {
        // CRITICAL is 5/min, IP-keyed (application.yaml). Registration is public and CRITICAL, so
        // no authentication is needed to exercise it - a real attacker wouldn't have any either.
        //
        // A distinct X-Forwarded-For, not MockMvc's default remote address, is used deliberately:
        // the Redis container is shared across the whole test JVM (see AbstractIntegrationTest),
        // and every MockMvc request reports the same default address - so without an IP of its
        // own, this test's CRITICAL:ip:<addr> counter could already be partially consumed by any
        // other test that hit a CRITICAL endpoint earlier in the same run, making this flaky and
        // order-dependent rather than a clean, isolated 5-then-429 sequence.
        String testIp = "203.0.113." + (int) (Math.random() * 250 + 1);

        for (int attempt = 1; attempt <= 5; attempt++) {
            String body = registerBody("attempt" + attempt + "-" + UUID.randomUUID());
            mvc.perform(post("/api/v1/register").header("X-Forwarded-For", testIp)
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isAccepted());
        }

        // The 6th request from the same IP within the same window must be denied, with a real
        // Retry-After header computed from the real Redis TTL, and a real RateLimitExceededEvent
        // published to the real audit listener.
        String sixthBody = registerBody("attempt6-" + UUID.randomUUID());
        mvc.perform(post("/api/v1/register").header("X-Forwarded-For", testIp)
                        .contentType(APPLICATION_JSON).content(sixthBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("problem+json")));
    }

    private String registerBody(String localPart) {
        return "{\"email\":\"" + localPart + "@example.com\",\"password\":\"a-genuinely-long-passphrase\","
                + "\"fullName\":\"Rate Limit Test\"}";
    }
}
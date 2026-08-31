package com.havenbank.backend.money.service;

import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.domain.PolicyKey;
import com.havenbank.backend.money.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Thresholds are read from effective-dated policy rows, never hard-coded.
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policies;
    @InjectMocks
    private PolicyService policyService;

    private Policy policyWorth(String value) {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        when(p.getValue()).thenReturn(new BigDecimal(value));
        return p;
    }

    @Test
    void failsLoudlyRatherThanGuessingWhenNoPolicyIsConfigured() {
        when(policies.findEffective(any(PolicyKey.class), any(Instant.class))).thenReturn(List.of());

        assertThatThrownBy(() -> policyService.stepUpThreshold())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STEP_UP_THRESHOLD");
    }

    @Test
    void readsTheEffectiveStepUpThreshold() {
        Policy policy = policyWorth("1000.00");
        when(policies.findEffective(eq(PolicyKey.STEP_UP_THRESHOLD), any(Instant.class)))
                .thenReturn(List.of(policy));

        assertThat(policyService.stepUpThreshold()).isEqualByComparingTo("1000.00");
    }

    @Test
    void readsTheEffectiveDailyLimit() {
        Policy policy = policyWorth("5000.00");
        when(policies.findEffective(eq(PolicyKey.DAILY_LIMIT), any(Instant.class)))
                .thenReturn(List.of(policy));

        assertThat(policyService.dailyLimit()).isEqualByComparingTo("5000.00");
    }

    @Test
    void usesTheFirstRowWhenSeveralVersionsAreEffective() {
        Policy current = policyWorth("5000.00");
        // Bare, unstubbed mock: the point of this test is that its value is never read at all
        // (only its presence in the list matters), so stubbing .getValue() on it would be dead
        // code that Mockito's strict-stubs check correctly flags as unnecessary.
        Policy stale = org.mockito.Mockito.mock(Policy.class);
        when(policies.findEffective(eq(PolicyKey.DAILY_LIMIT), any(Instant.class)))
                .thenReturn(List.of(current, stale));

        assertThat(policyService.dailyLimit()).isEqualByComparingTo("5000.00");
    }
}

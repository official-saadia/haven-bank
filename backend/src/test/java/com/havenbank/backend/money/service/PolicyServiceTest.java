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
    void readsTheEffectiveStepUpThreshold() {
        when(policies.findEffective(eq(PolicyKey.STEP_UP_THRESHOLD), any(Instant.class)))
                .thenReturn(List.of(policyWorth("1000.00")));

        assertThat(policyService.stepUpThreshold()).isEqualByComparingTo("1000.00");
    }

    @Test
    void readsTheEffectiveDailyLimit() {
        when(policies.findEffective(eq(PolicyKey.DAILY_LIMIT), any(Instant.class)))
                .thenReturn(List.of(policyWorth("5000.00")));

        assertThat(policyService.dailyLimit()).isEqualByComparingTo("5000.00");
    }

    @Test
    void usesTheFirstRowWhenSeveralVersionsAreEffective() {
        when(policies.findEffective(eq(PolicyKey.DAILY_LIMIT), any(Instant.class)))
                .thenReturn(List.of(policyWorth("5000.00"), policyWorth("1.00")));

        assertThat(policyService.dailyLimit()).isEqualByComparingTo("5000.00");
    }

    @Test
    void failsLoudlyRatherThanGuessingWhenNoPolicyIsConfigured() {
        when(policies.findEffective(any(PolicyKey.class), any(Instant.class))).thenReturn(List.of());

        assertThatThrownBy(() -> policyService.stepUpThreshold())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STEP_UP_THRESHOLD");
    }
}

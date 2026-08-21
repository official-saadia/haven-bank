package com.havenbank.backend.money.service;

import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.domain.PolicyKey;
import com.havenbank.backend.money.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reads effective policy thresholds (step-up amount, daily limit).
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policies;

    public BigDecimal stepUpThreshold() {
        return value(PolicyKey.STEP_UP_THRESHOLD);
    }

    public BigDecimal dailyLimit() {
        return value(PolicyKey.DAILY_LIMIT);
    }

    private BigDecimal value(PolicyKey key) {
        List<Policy> effective = policies.findEffective(key, Instant.now());
        if (effective.isEmpty()) {
            throw new IllegalStateException("No effective policy for " + key);
        }
        return effective.get(0).getValue();
    }
}

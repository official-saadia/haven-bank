package com.havenbank.backend.money.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A versioned policy threshold (step-up amount, daily limit). Same versioning pattern as
 * {@link FeeSchedule}: changes insert a new effective-dated row.
 */
@Entity
@Table(name = "policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_key", nullable = false, length = 32)
    private PolicyKey policyKey;

    @Column(nullable = false, length = 32)
    private String scope;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Builder
    private Policy(PolicyKey policyKey, String scope, BigDecimal value, Instant effectiveFrom) {
        this.id = UUID.randomUUID();
        this.policyKey = policyKey;
        this.scope = scope;
        this.value = value;
        this.effectiveFrom = effectiveFrom;
    }

    public void close(Instant at) {
        this.effectiveTo = at;
    }
}

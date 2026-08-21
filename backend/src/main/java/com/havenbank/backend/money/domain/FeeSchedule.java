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
 * A versioned fee rate. Rate changes INSERT a new row with a new {@code effectiveFrom}; old rows are
 * retained so historical transactions can point at the exact version that applied.
 */
@Entity
@Table(name = "fee_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeSchedule {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 16)
    private TransactionType appliesTo;

    @Column(name = "tier_min", precision = 19, scale = 4)
    private BigDecimal tierMin;

    @Column(name = "tier_max", precision = 19, scale = 4)
    private BigDecimal tierMax;

    @Column(name = "fee_flat", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeFlat;

    @Column(name = "fee_percent", nullable = false, precision = 6, scale = 4)
    private BigDecimal feePercent;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Builder
    private FeeSchedule(TransactionType appliesTo, BigDecimal tierMin, BigDecimal tierMax,
                        BigDecimal feeFlat, BigDecimal feePercent, Instant effectiveFrom) {
        this.id = UUID.randomUUID();
        this.appliesTo = appliesTo;
        this.tierMin = tierMin;
        this.tierMax = tierMax;
        this.feeFlat = feeFlat;
        this.feePercent = feePercent;
        this.effectiveFrom = effectiveFrom;
    }

    /**
     * Close this version so a newer one supersedes it.
     */
    public void close(Instant at) {
        this.effectiveTo = at;
    }
}

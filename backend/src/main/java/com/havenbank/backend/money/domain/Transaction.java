package com.havenbank.backend.money.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The business-event header for a money movement. Owns one-to-many {@link LedgerEntry} rows (by id
 * reference). Holds the idempotency key (unique) and the applied fee/fee-schedule version.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 32)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "initiating_user_id", nullable = false)
    private UUID initiatingUserId;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;        // null for DEPOSIT

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;   // null for WITHDRAWAL

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "fee_schedule_id")
    private UUID feeScheduleId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    private Transaction(String referenceNumber, TransactionType type, UUID initiatingUserId,
                        UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount,
                        BigDecimal feeAmount, UUID feeScheduleId, String currency,
                        String idempotencyKey, String correlationId) {
        this.id = UUID.randomUUID();
        this.referenceNumber = referenceNumber;
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.initiatingUserId = initiatingUserId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.feeAmount = feeAmount == null ? BigDecimal.ZERO : feeAmount;
        this.feeScheduleId = feeScheduleId;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

package com.havenbank.backend.money.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An account. Customer accounts carry a {@code userId}; bank-internal accounts (CASH, FEE_INCOME)
 * have a null {@code userId} and category {@link AccountCategory#INTERNAL}. Balance is derived from
 * the ledger. A {@link Version} column provides optimistic locking for status changes; money
 * movement additionally takes a pessimistic lock on the row (see the repository).
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;                 // null for INTERNAL accounts

    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", nullable = false, length = 16)
    private AccountCategory category;

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * The client's Idempotency-Key for the request that opened this account; null for internal
     * and seed accounts. A unique index makes it the real double-submit guard (see V6).
     */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Version
    private long version;

    @Builder
    private Account(UUID userId, AccountCategory category, String accountNumber,
                    AccountType type, String currency, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.category = category;
        this.accountNumber = accountNumber;
        this.type = type;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = AccountStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId != null && userId.equals(candidate);
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public void freeze() {
        this.status = AccountStatus.FROZEN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
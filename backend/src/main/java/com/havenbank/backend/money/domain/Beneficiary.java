package com.havenbank.backend.money.domain;

import com.havenbank.backend.shared.crypto.CryptoConverter;
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
 * A saved payee belonging to one customer — an address-book entry, nothing more. It grants no
 * authority: a transfer to a saved beneficiary is validated exactly as a transfer to a
 * hand-typed account number, because the row itself is unverified user input.
 *
 * <p>{@code name} and {@code nickname} are third-party PII and are encrypted at rest
 * ({@link CryptoConverter}, NFR-1.5). {@code accountNumber} stays plaintext so the
 * {@code (user_id, account_number)} uniqueness constraint can be enforced by the database;
 * it is masked to its last four characters everywhere it is logged or audited (FR-5.3).
 */
@Entity
@Table(name = "beneficiaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Beneficiary {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = CryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String name;

    @Convert(converter = CryptoConverter.class)
    @Column(length = 512)
    private String nickname;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private Beneficiary(UUID userId, String name, String nickname, String accountNumber) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.nickname = nickname;
        this.accountNumber = accountNumber;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId != null && userId.equals(candidate);
    }

    public void rename(String name, String nickname) {
        this.name = name;
        this.nickname = nickname;
    }

    /**
     * Last four characters only — safe for logs, audit records and notifications (FR-5.3).
     */
    public String maskedAccountNumber() {
        return mask(accountNumber);
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "••••";
        }
        return "••••" + accountNumber.substring(accountNumber.length() - 4);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Beneficiary other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

package com.havenbank.backend.iam.domain;

import com.havenbank.backend.shared.crypto.CryptoConverter;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A platform user (customer or staff). Stores only a one-way password hash, never a plaintext
 * password (FR-1.3). Identity/equality is based on the immutable surrogate {@code id}, using an
 * {@code instanceof} check that is safe under Hibernate proxying.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "full_name", nullable = false, length = 512)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // LAZY: roles are not needed on every User load. The login path fetches them (and their
    // permissions) explicitly via an entity graph on UserRepository.findByEmailIgnoreCase; other
    // paths that read roles do so inside an open transaction, and @BatchSize collapses what would
    // otherwise be N+1 selects across a page of users into a handful of batched IN queries.
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private User(String email, String passwordHash, String fullName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = UserStatus.PENDING_VERIFICATION;
        this.emailVerified = false;
    }

    // --- Behaviour (kept on the aggregate rather than leaking setters) ---

    /**
     * Mark the account verified and active. Idempotent.
     */
    public void markEmailVerified() {
        this.emailVerified = true;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Replace the stored password hash (callers must pass an already-encoded value).
     */
    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Replace the user's role set (administrative operation).
     */
    public void replaceRoles(java.util.Set<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    /**
     * Lock the account. A permanently closed account cannot be locked.
     */
    public void lock() {
        if (status == UserStatus.CLOSED) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "A closed account cannot be locked");
        }
        this.status = UserStatus.LOCKED;
    }

    /**
     * Unlock a locked account, restoring its prior state (never silently verifying it).
     */
    public void unlock() {
        if (status != UserStatus.LOCKED) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "Only a locked account can be unlocked");
        }
        this.status = emailVerified ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
    }

    /**
     * Permanently close the account. Idempotent; there is no path back to ACTIVE.
     */
    public void deactivate() {
        if (status == UserStatus.CLOSED) return;
        this.status = UserStatus.CLOSED;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
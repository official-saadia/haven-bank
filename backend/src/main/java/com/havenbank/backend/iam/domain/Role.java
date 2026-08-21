package com.havenbank.backend.iam.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A named collection of {@link Permission}s (e.g. {@code CUSTOMER}, {@code STAFF}, {@code ADMIN}).
 * The role &harr; permission link is the {@code role_permissions} join table. Equality is by the
 * natural key {@code name}.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(length = 256)
    private String description;

    // LAZY: permissions are only read when building authorities (login) or listing/administering
    // roles. The login path pulls them via the two-level entity graph on findByEmailIgnoreCase;
    // RoleRepository.findAll pulls them via its own graph; @BatchSize covers the remaining
    // per-role reads so a set of roles costs a batched IN query rather than one select each.
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private Role(String name, String description) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void replacePermissions(Set<Permission> newPermissions) {
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role other)) return false;
        return name != null && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
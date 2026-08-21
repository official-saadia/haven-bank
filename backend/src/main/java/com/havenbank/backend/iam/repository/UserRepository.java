package com.havenbank.backend.iam.repository;

import com.havenbank.backend.iam.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * The authentication hot path: one user, fetched together with roles and each role's
     * permissions so {@code AppUserPrincipal} can build authorities with no follow-up selects.
     * Both associations are {@code Set}s, so a two-level fetch is safe (no bag / cartesian issues).
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(u) from User u join u.roles r where r.id = :roleId")
    long countUsersWithRole(@org.springframework.data.repository.query.Param("roleId") java.util.UUID roleId);

    @Query(
            "select count(u) > 0 from User u join u.roles r where r.name = :roleName")
    boolean existsByRoleName(@org.springframework.data.repository.query.Param("roleName") String roleName);
}
package com.havenbank.backend.iam.repository;

import com.havenbank.backend.iam.domain.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Role administration lists every role with its permissions. A dedicated method (rather than an
     * override of {@code findAll()}) keeps the graph off the inherited CRUD path, so a plain
     * {@code findAll()} stays a cheap role-only query. Not paginated, so the collection fetch is
     * safe here and avoids N+1.
     */
    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r")
    List<Role> findAllWithPermissions();

    Optional<Role> findByName(String name);

    boolean existsByName(String name);
}
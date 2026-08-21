package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every finder is scoped by {@code userId}. There is deliberately no {@code findById} in use:
 * a lookup by id alone would return another customer's row and leave the ownership check to the
 * caller, which is precisely how IDOR bugs are written.
 *
 * <p>Ordering is by {@code createdAt} rather than name because {@code name} is stored encrypted —
 * a SQL {@code ORDER BY} over ciphertext would sort on nothing meaningful. Alphabetical ordering
 * happens in the service, after decryption.
 */
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Beneficiary> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndAccountNumber(UUID userId, String accountNumber);

    Optional<Beneficiary> findByUserIdAndAccountNumber(UUID userId, String accountNumber);
}

package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.Account;
import com.havenbank.backend.money.domain.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserId(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * For idempotent account opening: a replayed key returns the account it first created.
     * Scoped by user so one customer's key can never surface another's account.
     */
    Optional<Account> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<Account> findFirstByType(AccountType type);

    /**
     * Pessimistic write lock: serialises concurrent money movement on the same account row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
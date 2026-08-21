package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.Transaction;
import com.havenbank.backend.money.domain.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select t from Transaction t
            where t.sourceAccountId = :accountId or t.destinationAccountId = :accountId
            order by t.createdAt desc
            """)
    Page<Transaction> findForAccount(@Param("accountId") UUID accountId, Pageable pageable);

    @Query("""
            select t from Transaction t
            where (t.sourceAccountId = :accountId or t.destinationAccountId = :accountId)
              and t.createdAt >= :from and t.createdAt <= :to
            order by t.createdAt asc
            """)
    java.util.List<Transaction> findForStatement(@Param("accountId") UUID accountId,
                                                 @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Cumulative outbound value initiated by a user since {@code since} (for the daily limit).
     */
    @Query("""
            select coalesce(sum(t.amount + t.feeAmount), 0) from Transaction t
            where t.initiatingUserId = :userId
              and t.status = :status
              and t.type in (com.havenbank.backend.money.domain.TransactionType.TRANSFER,
                             com.havenbank.backend.money.domain.TransactionType.WITHDRAWAL)
              and t.createdAt >= :since
            """)
    BigDecimal outboundSince(@Param("userId") UUID userId,
                             @Param("status") TransactionStatus status,
                             @Param("since") Instant since);

    List<Transaction> findByInitiatingUserId(UUID userId);
}

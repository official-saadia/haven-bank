package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Derive an account's balance from its postings: sum(credits) - sum(debits).
     */
    @Query("""
            select coalesce(sum(case when e.direction = com.havenbank.backend.money.domain.LedgerDirection.CREDIT
                                     then e.amount else e.amount * -1 end), 0)
            from LedgerEntry e where e.accountId = :accountId
            """)
    BigDecimal balanceOf(@Param("accountId") UUID accountId);
}

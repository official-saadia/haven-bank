package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.FeeSchedule;
import com.havenbank.backend.money.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, UUID> {

    java.util.List<FeeSchedule> findByAppliesToAndEffectiveToIsNull(TransactionType appliesTo);


    /**
     * Effective fee rows for a movement type and amount at a given instant, newest first.
     */
    @Query("""
            select f from FeeSchedule f
            where f.appliesTo = :type
              and f.effectiveFrom <= :at
              and (f.effectiveTo is null or f.effectiveTo > :at)
              and (f.tierMin is null or f.tierMin <= :amount)
              and (f.tierMax is null or f.tierMax >= :amount)
            order by f.effectiveFrom desc
            """)
    List<FeeSchedule> findEffective(@Param("type") TransactionType type,
                                    @Param("amount") BigDecimal amount,
                                    @Param("at") Instant at);
}

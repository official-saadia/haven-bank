package com.havenbank.backend.money.repository;

import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.domain.PolicyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    java.util.List<Policy> findByPolicyKeyAndScopeAndEffectiveToIsNull(PolicyKey policyKey, String scope);


    @Query("""
            select p from Policy p
            where p.policyKey = :key
              and p.effectiveFrom <= :at
              and (p.effectiveTo is null or p.effectiveTo > :at)
            order by p.effectiveFrom desc
            """)
    List<Policy> findEffective(@Param("key") PolicyKey key, @Param("at") Instant at);
}

package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.domain.PolicyKey;
import com.havenbank.backend.money.dto.PolicyRequest;
import com.havenbank.backend.money.dto.PolicyResponse;
import com.havenbank.backend.money.repository.PolicyRepository;
import com.havenbank.backend.money.mapper.PolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Versioned policy administration (ADMIN) for the step-up threshold and daily limit.
 */
@Service
@RequiredArgsConstructor
public class PolicyAdminService {

    private final PolicyRepository policies;
    private final AuditService auditService;
    private final PolicyMapper policyMapper;

    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        return policies.findAll().stream().map(policyMapper::toResponse).toList();
    }

    @Transactional
    public PolicyResponse createVersion(UUID actor, PolicyRequest request) {
        PolicyKey key = PolicyKey.valueOf(request.policyKey());
        Instant now = Instant.now();
        policies.findByPolicyKeyAndScopeAndEffectiveToIsNull(key, request.scope()).forEach(p -> p.close(now));

        Policy created = policies.save(Policy.builder()
                .policyKey(key).scope(request.scope()).value(request.value()).effectiveFrom(now).build());
        auditService.record(AuditEvent.success(actor, AuditAction.POLICY_VERSIONED, key.name()));
        return policyMapper.toResponse(created);
    }

}

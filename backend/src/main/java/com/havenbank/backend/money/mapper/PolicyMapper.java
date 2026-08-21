package com.havenbank.backend.money.mapper;

import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.dto.PolicyResponse;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Policy} to {@link PolicyResponse}.
 */
@Component
public class PolicyMapper {

    public PolicyResponse toResponse(Policy p) {
        return new PolicyResponse(p.getId(), p.getPolicyKey().name(), p.getScope(), p.getValue(),
                p.getEffectiveFrom(), p.getEffectiveTo());
    }
}

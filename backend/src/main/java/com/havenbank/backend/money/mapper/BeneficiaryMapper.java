package com.havenbank.backend.money.mapper;

import com.havenbank.backend.money.domain.Beneficiary;
import com.havenbank.backend.money.dto.BeneficiaryResponse;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Beneficiary} to {@link BeneficiaryResponse}. The owner's own read returns the full
 * account number (masking applies only to logs/audit/notifications, not the owner's own data).
 */
@Component
public class BeneficiaryMapper {

    public BeneficiaryResponse toResponse(Beneficiary b) {
        return new BeneficiaryResponse(b.getId(), b.getName(), b.getNickname(),
                b.getAccountNumber(), b.getCreatedAt());
    }
}

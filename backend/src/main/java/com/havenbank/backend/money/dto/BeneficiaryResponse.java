package com.havenbank.backend.money.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A saved payee as returned to its owner. The full account number is included because the owner
 * supplied it and needs it to complete a transfer; masking applies to logs, audit records and
 * notifications (FR-5.3), not to the owner's own read of their own data.
 */
public record BeneficiaryResponse(
        UUID id,
        String name,
        String nickname,
        String accountNumber,
        Instant createdAt
) {
}

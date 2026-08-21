package com.havenbank.backend.money.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Safe projection of an account plus its derived balance.
 */
public record AccountResponse(
        UUID id,
        String accountNumber,
        String type,
        String currency,
        String status,
        BigDecimal balance
) {
}

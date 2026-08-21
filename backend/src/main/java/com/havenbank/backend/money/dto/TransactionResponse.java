package com.havenbank.backend.money.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * History row for an account, from the account's point of view.
 */
public record TransactionResponse(
        UUID id,
        String reference,
        String type,
        String status,
        BigDecimal amount,
        BigDecimal fee,
        String direction,     // DEBIT or CREDIT relative to the queried account
        BigDecimal balanceAfter,
        Instant createdAt
) {
}

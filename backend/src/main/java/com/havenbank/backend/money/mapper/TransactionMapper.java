package com.havenbank.backend.money.mapper;

import com.havenbank.backend.money.domain.Transaction;
import com.havenbank.backend.money.dto.TransactionResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Maps {@link Transaction} to {@link TransactionResponse}. Direction (DEBIT/CREDIT) is relative to
 * the account the history is being read from, so that account id is passed in as context.
 */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction t, UUID viewingAccountId) {
        boolean isSource = viewingAccountId.equals(t.getSourceAccountId());
        return new TransactionResponse(t.getId(), t.getReferenceNumber(), t.getType().name(),
                t.getStatus().name(), t.getAmount(), t.getFeeAmount(),
                isSource ? "DEBIT" : "CREDIT", null, t.getCreatedAt());
    }
}

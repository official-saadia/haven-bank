package com.havenbank.backend.money.service;

import com.havenbank.backend.money.domain.Transaction;
import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.dto.TransactionResponse;
import com.havenbank.backend.money.repository.AccountRepository;
import com.havenbank.backend.money.repository.TransactionRepository;
import com.havenbank.backend.money.repository.TransactionSpecs;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import com.havenbank.backend.money.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side history + statement for an owned account, filterable by type/date/amount (FR-4.2).
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(UUID userId, UUID accountId, TransactionType type,
                                             Instant from, Instant to, BigDecimal minAmount,
                                             BigDecimal maxAmount, Pageable pageable) {
        requireOwned(userId, accountId);
        // Sort comes from the Pageable so the caller controls it; the controller defaults to
        // createdAt desc (FR-4.1).
        return transactions
                .findAll(TransactionSpecs.history(accountId, type, from, to, minAmount, maxAmount),
                        pageable)
                .map(t -> transactionMapper.toResponse(t, accountId));
    }

    @Transactional(readOnly = true)
    public String statementCsv(UUID userId, UUID accountId, Instant from, Instant to) {
        requireOwned(userId, accountId);
        List<Transaction> rows = transactions.findForStatement(accountId, from, to);
        StringBuilder sb = new StringBuilder("Date,Reference,Type,Direction,Amount,Fee,Status\n");
        for (Transaction t : rows) {
            String direction = accountId.equals(t.getSourceAccountId()) ? "DEBIT" : "CREDIT";
            sb.append(t.getCreatedAt()).append(',')
                    .append(t.getReferenceNumber()).append(',')
                    .append(t.getType()).append(',')
                    .append(direction).append(',')
                    .append(t.getAmount()).append(',')
                    .append(t.getFeeAmount()).append(',')
                    .append(t.getStatus()).append('\n');
        }
        return sb.toString();
    }

    private void requireOwned(UUID userId, UUID accountId) {
        accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

}
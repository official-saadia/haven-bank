package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.Account;
import com.havenbank.backend.money.domain.AccountCategory;
import com.havenbank.backend.money.domain.AccountType;
import com.havenbank.backend.money.dto.AccountResponse;
import com.havenbank.backend.money.repository.AccountRepository;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import com.havenbank.backend.money.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account lifecycle and read queries. Every read is scoped by the authenticated owner.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final AuditService auditService;
    private final AccountMapper accountMapper;

    @Transactional
    public AccountResponse open(UUID userId, String type, String currency, String idempotencyKey) {
        // Replay of a key we've already honoured returns the account it first created, so a retried
        // or double-fired request never opens a second account (FR-3.7 semantics, applied here).
        if (idempotencyKey != null) {
            Optional<Account> existing = accounts.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        Account account = Account.builder()
                .userId(userId)
                .category(AccountCategory.CUSTOMER)
                .accountNumber(uniqueAccountNumber())
                .type(AccountType.valueOf(type))
                .currency(currency)
                .idempotencyKey(idempotencyKey)
                .build();
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException race) {
            // Two concurrent requests with the same key: the unique index rejects the loser, which
            // then reads back the winner's account instead of failing. Re-throws if the clash was
            // something else (e.g. a duplicate account number, which should surface).
            if (idempotencyKey != null) {
                Optional<Account> winner = accounts.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
                if (winner.isPresent()) {
                    return toResponse(winner.get());
                }
            }
            throw race;
        }
        auditService.record(AuditEvent.success(userId, AuditAction.ACCOUNT_OPENED,
                account.getAccountNumber()));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listOwn(UUID userId) {
        return accounts.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getOwned(UUID userId, UUID accountId) {
        // IDOR-safe: a non-owned or non-existent account is indistinguishable (404).
        Account account = accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return toResponse(account);
    }

    @Transactional
    public void close(UUID userId, UUID accountId) {
        Account account = accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (ledger.balanceOf(account.getId()).signum() != 0) {
            throw new BusinessException(ErrorType.BUSINESS_RULE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Account must have a zero balance before it can be closed");
        }
        account.close();
        auditService.record(AuditEvent.success(userId, AuditAction.ACCOUNT_CLOSED,
                account.getAccountNumber()));
    }

    private AccountResponse toResponse(Account a) {
        // Field mapping is delegated to AccountMapper; the service supplies the ledger-derived balance.
        return accountMapper.toResponse(a, ledger.balanceOf(a.getId()));
    }

    private String uniqueAccountNumber() {
        for (int i = 0; i < 8; i++) {
            String candidate = String.format("%010d", (long) (RANDOM.nextDouble() * 1_000_000_0000L));
            if (accounts.findByAccountNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique account number");
    }
}
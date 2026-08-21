package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.*;
import com.havenbank.backend.money.dto.AccountResponse;
import com.havenbank.backend.money.dto.TransferRequest;
import com.havenbank.backend.money.repository.AccountRepository;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.money.repository.TransactionRepository;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import com.havenbank.backend.money.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * The double-entry money-movement engine. Deposit, withdraw and transfer each write a balanced set of
 * ledger entries under one {@link Transaction} in a single database transaction (FR-3.5). Guarantees:
 * <ul>
 *   <li><strong>Idempotency</strong> - a repeated key is rejected without re-executing (FR-3.7);</li>
 *   <li><strong>Concurrency</strong> - accounts are pessimistically locked for the movement (FR-3.8);</li>
 *   <li><strong>Fees</strong> - transfer fees post extra entries against a bank FEE_INCOME account;</li>
 *   <li><strong>Limits</strong> - a rolling daily outbound limit and a step-up threshold are enforced.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MoneyMovementService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransactionRepository transactions;
    private final FeeService feeService;
    private final PolicyService policyService;
    private final StepUpService stepUpService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.havenbank.backend.iam.repository.UserRepository users;
    private final AccountMapper accountMapper;

    @Transactional
    public AccountResponse deposit(UUID userId, String email, UUID accountId, BigDecimal amount,
                                   String idempotencyKey) {
        guardIdempotency(idempotencyKey);
        Account account = lockOwned(userId, accountId);
        Account cash = internal(AccountType.CASH);
        BigDecimal value = scale(amount);

        Transaction txn = transactions.save(newTxn(TransactionType.DEPOSIT, userId, null,
                account.getId(), value, BigDecimal.ZERO, null, account.getCurrency(), idempotencyKey));
        post(txn.getId(), account.getId(), LedgerDirection.CREDIT, value, true);
        post(txn.getId(), cash.getId(), LedgerDirection.DEBIT, value, false);
        txn.markCompleted();

        auditService.record(AuditEvent.success(userId, AuditAction.DEPOSIT, txn.getReferenceNumber()));
        notify(account, email, "a deposit of " + value + " " + account.getCurrency());
        return response(account);
    }

    @Transactional
    public AccountResponse withdraw(UUID userId, String email, UUID accountId, BigDecimal amount,
                                    String idempotencyKey) {
        guardIdempotency(idempotencyKey);
        Account account = lockOwned(userId, accountId);
        Account cash = internal(AccountType.CASH);
        BigDecimal value = scale(amount);
        requireFunds(account, value);

        Transaction txn = transactions.save(newTxn(TransactionType.WITHDRAWAL, userId, account.getId(),
                null, value, BigDecimal.ZERO, null, account.getCurrency(), idempotencyKey));
        post(txn.getId(), account.getId(), LedgerDirection.DEBIT, value, true);
        post(txn.getId(), cash.getId(), LedgerDirection.CREDIT, value, false);
        txn.markCompleted();

        auditService.record(AuditEvent.success(userId, AuditAction.WITHDRAWAL, txn.getReferenceNumber()));
        notify(account, email, "a withdrawal of " + value + " " + account.getCurrency());
        return response(account);
    }

    @Transactional
    public AccountResponse transfer(UUID userId, String email, String name, TransferRequest req,
                                    String idempotencyKey) {
        guardIdempotency(idempotencyKey);
        BigDecimal value = scale(req.amount());

        Account source = lockOwned(userId, req.sourceAccountId());
        Account destination = resolveDestination(req);

        FeeService.FeeResult fee = feeService.feeFor(TransactionType.TRANSFER, value);
        BigDecimal total = value.add(fee.amount());

        enforceDailyLimit(userId, total);
        enforceStepUp(userId, email, name, value, req.otp());
        requireFunds(source, total);

        Account feeIncome = internal(AccountType.FEE_INCOME);
        Transaction txn = transactions.save(newTxn(TransactionType.TRANSFER, userId, source.getId(),
                destination.getId(), value, fee.amount(), fee.scheduleId(), source.getCurrency(), idempotencyKey));

        post(txn.getId(), source.getId(), LedgerDirection.DEBIT, value, true);
        post(txn.getId(), destination.getId(), LedgerDirection.CREDIT, value, destination.getUserId() != null);
        if (fee.amount().signum() > 0) {
            post(txn.getId(), source.getId(), LedgerDirection.DEBIT, fee.amount(), true);
            post(txn.getId(), feeIncome.getId(), LedgerDirection.CREDIT, fee.amount(), false);
        }
        txn.markCompleted();

        auditService.record(AuditEvent.success(userId, AuditAction.TRANSFER, txn.getReferenceNumber()));
        notify(source, email, "a transfer of " + value + " " + source.getCurrency());
        return response(source);
    }

    // --- helpers -------------------------------------------------------------

    private void guardIdempotency(String idempotencyKey) {
        if (transactions.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "This request has already been processed");
        }
    }

    private Account lockOwned(UUID userId, UUID accountId) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.isOwnedBy(userId)) {
            throw new ResourceNotFoundException("Account not found"); // IDOR-safe
        }
        if (!account.isActive()) {
            throw new BusinessException(ErrorType.BUSINESS_RULE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Account is not active");
        }
        return account;
    }

    private Account resolveDestination(TransferRequest req) {
        if (req.destinationAccountId() != null) {
            return accounts.findByIdForUpdate(req.destinationAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        }
        if (req.destinationAccountNumber() != null) {
            Account destination = accounts.findByAccountNumber(req.destinationAccountNumber())
                    .orElseThrow(this::beneficiaryRejected);
            requireNameMatches(destination, req.beneficiaryName());
            return destination;
        }
        throw new BusinessException(ErrorType.VALIDATION, HttpStatus.BAD_REQUEST,
                "Enter an account number to send to.", "destinationAccountNumber");
    }

    /**
     * FR-3.4: a third-party transfer is validated against the account number <em>and</em> the
     * beneficiary name, so a mistyped digit that happens to land on a real account is caught before
     * the money moves.
     *
     * <p>The failure is deliberately identical to "no such account". Distinguishing them would
     * confirm that an account number is real and then allow the name to be brute-forced a guess at
     * a time — the same enumeration disclosure FR-2.3 exists to prevent. One error, one meaning
     * to an attacker: "that pair is not valid."
     */
    private void requireNameMatches(Account destination, String claimedName) {
        if (destination.getUserId() == null) {
            return; // internal bank account (cash, fee income) — not a customer, no name to match
        }
        String actual = users.findById(destination.getUserId())
                .map(u -> normalise(u.getFullName()))
                .orElseThrow(this::beneficiaryRejected);
        if (!actual.equals(normalise(claimedName))) {
            throw beneficiaryRejected();
        }
    }

    /**
     * Case, spacing and punctuation are noise; a name is not a password.
     */
    private static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private BusinessException beneficiaryRejected() {
        // The vague wording is deliberate (FR-3.10/FR-2.3): it must not reveal whether the number or
        // the name was wrong. Attaching it to the account-number field is safe — the field is where
        // the user re-checks the pair — and does not disclose which half failed.
        return new BusinessException(ErrorType.BUSINESS_RULE, HttpStatus.UNPROCESSABLE_ENTITY,
                "Beneficiary account could not be validated", "destinationAccountNumber");
    }

    private void requireFunds(Account account, BigDecimal needed) {
        if (ledger.balanceOf(account.getId()).compareTo(needed) < 0) {
            throw new BusinessException(ErrorType.BUSINESS_RULE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient funds for this transfer.", "amount");
        }
    }

    private void enforceDailyLimit(UUID userId, BigDecimal total) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal usedToday = transactions.outboundSince(userId, TransactionStatus.COMPLETED, startOfDay);
        if (usedToday.add(total).compareTo(policyService.dailyLimit()) > 0) {
            throw new BusinessException(ErrorType.BUSINESS_RULE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "This would exceed your daily transfer limit.", "amount");
        }
    }

    private void enforceStepUp(UUID userId, String email, String name, BigDecimal amount, String otp) {
        if (amount.compareTo(policyService.stepUpThreshold()) <= 0) {
            return;
        }
        if (otp != null) {
            stepUpService.verify(userId, otp);
        }
        if (!stepUpService.isElevated(userId)) {
            stepUpService.issueChallenge(userId, email, name);
            throw new BusinessException(ErrorType.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "This transfer needs confirmation. We've emailed you a code - resubmit it with the transfer.");
        }
        stepUpService.consumeElevation(userId);
    }

    private Transaction newTxn(TransactionType type, UUID userId, UUID src, UUID dst, BigDecimal amount,
                               BigDecimal fee, UUID feeScheduleId, String currency, String idempotencyKey) {
        return Transaction.builder()
                .referenceNumber("TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                .type(type).initiatingUserId(userId)
                .sourceAccountId(src).destinationAccountId(dst)
                .amount(amount).feeAmount(fee).feeScheduleId(feeScheduleId)
                .currency(currency).idempotencyKey(idempotencyKey)
                .correlationId(MDC.get("correlationId"))
                .build();
    }

    private void post(UUID txnId, UUID accountId, LedgerDirection direction, BigDecimal amount,
                      boolean recordBalance) {
        BigDecimal balanceAfter = null;
        if (recordBalance) {
            BigDecimal current = ledger.balanceOf(accountId);
            balanceAfter = direction == LedgerDirection.CREDIT ? current.add(amount) : current.subtract(amount);
        }
        ledger.save(LedgerEntry.builder()
                .transactionId(txnId).accountId(accountId).direction(direction)
                .amount(amount).balanceAfter(balanceAfter).build());
    }

    private Account internal(AccountType type) {
        return accounts.findFirstByType(type)
                .orElseThrow(() -> new IllegalStateException("Missing internal account: " + type));
    }

    private void notify(Account account, String email, String summary) {
        if (account.getUserId() == null) return;
        notificationService.send(new NotificationMessage(email, null, account.getUserId(),
                NotificationType.MONEY_MOVEMENT, Map.of("summary", summary)));
    }

    private AccountResponse response(Account a) {
        return accountMapper.toResponse(a, ledger.balanceOf(a.getId()));
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_EVEN);
    }
}
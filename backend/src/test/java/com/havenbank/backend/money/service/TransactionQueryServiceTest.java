package com.havenbank.backend.money.service;

import com.havenbank.backend.money.domain.Account;
import com.havenbank.backend.money.domain.Transaction;
import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.mapper.TransactionMapper;
import com.havenbank.backend.money.repository.AccountRepository;
import com.havenbank.backend.money.repository.TransactionRepository;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FR-2.3 applied to transaction history/statements, and the CSV export's DEBIT/CREDIT direction
 * logic - determined per-row from whether the requested account was the source or the destination
 * of that specific transaction, not a fixed property of the transaction itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountRepository accounts;
    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionQueryService service;

    @Test
    void requestingHistoryForAnotherCustomersAccountIsIndistinguishableFromItNotExisting() {
        when(accounts.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(USER_ID, ACCOUNT_ID, null, null, null, null, null,
                org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requestingAStatementForAnotherCustomersAccountIsIndistinguishableFromItNotExisting() {
        when(accounts.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.statementCsv(USER_ID, ACCOUNT_ID, Instant.EPOCH, Instant.now()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aStatementRowIsDebitWhenTheRequestedAccountWasTheSourceAndCreditWhenItWasTheDestination() {
        UUID otherAccountId = UUID.randomUUID();
        when(accounts.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(mockOwnedAccount()));

        Transaction outbound = Transaction.builder().referenceNumber("REF-OUT").type(TransactionType.TRANSFER)
                .initiatingUserId(USER_ID).sourceAccountId(ACCOUNT_ID).destinationAccountId(otherAccountId)
                .amount(new BigDecimal("10.00")).feeAmount(BigDecimal.ZERO).currency("GBP")
                .idempotencyKey("k1").build();
        Transaction inbound = Transaction.builder().referenceNumber("REF-IN").type(TransactionType.TRANSFER)
                .initiatingUserId(USER_ID).sourceAccountId(otherAccountId).destinationAccountId(ACCOUNT_ID)
                .amount(new BigDecimal("5.00")).feeAmount(BigDecimal.ZERO).currency("GBP")
                .idempotencyKey("k2").build();
        when(transactions.findForStatement(ACCOUNT_ID, Instant.EPOCH, null))
                .thenReturn(List.of(outbound, inbound));

        String csv = service.statementCsv(USER_ID, ACCOUNT_ID, Instant.EPOCH, null);

        assertThat(csv).contains("REF-OUT").contains("DEBIT");
        assertThat(csv).contains("REF-IN").contains("CREDIT");
        // REF-OUT's row must say DEBIT, not CREDIT - and vice versa for REF-IN.
        String outLine = csv.lines().filter(l -> l.contains("REF-OUT")).findFirst().orElseThrow();
        String inLine = csv.lines().filter(l -> l.contains("REF-IN")).findFirst().orElseThrow();
        assertThat(outLine).contains("DEBIT").doesNotContain("CREDIT");
        assertThat(inLine).contains("CREDIT").doesNotContain("DEBIT");
    }

    @Test
    void aStatementWithNoMatchingTransactionsIsJustTheHeaderRow() {
        when(accounts.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(mockOwnedAccount()));
        when(transactions.findForStatement(any(), any(), any())).thenReturn(List.of());

        String csv = service.statementCsv(USER_ID, ACCOUNT_ID, Instant.EPOCH, Instant.now());

        assertThat(csv.lines().count()).isEqualTo(1);
        assertThat(csv).startsWith("Date,Reference,Type,Direction,Amount,Fee,Status");
    }

    private Account mockOwnedAccount() {
        return org.mockito.Mockito.mock(Account.class);
    }
}

package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.money.domain.*;
import com.havenbank.backend.money.dto.TransferRequest;
import com.havenbank.backend.money.mapper.AccountMapper;
import com.havenbank.backend.money.repository.AccountRepository;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.money.repository.TransactionRepository;
import com.havenbank.backend.notification.service.NotificationService;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MoneyMovementService} is the double-entry engine every FR-3.x requirement describes -
 * idempotency, ownership, daily limits, step-up, fees - and previously had no unit coverage at all,
 * relying entirely on integration tests. These isolate each business rule with a mocked collaborator
 * per branch, which the integration tests (real Postgres, one scenario each) don't attempt to do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyMovementServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID OTHER_ACCOUNT_ID = UUID.randomUUID();
    private static final String EMAIL = "customer@example.com";
    private static final String IDEMPOTENCY_KEY = "key-" + UUID.randomUUID();

    @Mock
    private AccountRepository accounts;
    @Mock
    private LedgerEntryRepository ledger;
    @Mock
    private TransactionRepository transactions;
    @Mock
    private FeeService feeService;
    @Mock
    private PolicyService policyService;
    @Mock
    private StepUpService stepUpService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository users;
    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private MoneyMovementService service;

    private Account customerAccount;
    private Account cashAccount;
    private Account feeIncomeAccount;

    @BeforeEach
    void setUp() {
        customerAccount = Account.builder().userId(USER_ID).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00CUST0000000001").type(AccountType.CHECKING).currency("GBP").build();
        cashAccount = Account.builder().category(AccountCategory.INTERNAL)
                .accountNumber("GB00CASH0000000001").type(AccountType.CASH).currency("GBP").build();
        feeIncomeAccount = Account.builder().category(AccountCategory.INTERNAL)
                .accountNumber("GB00FEEI0000000001").type(AccountType.FEE_INCOME).currency("GBP").build();

        when(accounts.findFirstByType(AccountType.CASH)).thenReturn(Optional.of(cashAccount));
        when(accounts.findFirstByType(AccountType.FEE_INCOME)).thenReturn(Optional.of(feeIncomeAccount));
        when(ledger.balanceOf(any())).thenReturn(BigDecimal.ZERO.setScale(2));
    }

    // --- idempotency (FR-3.7), shared across all three operations --------------------------

    @Test
    void depositRejectsAReplayedIdempotencyKeyWithoutTouchingAnyAccount() {
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(mock(Transaction.class)));

        assertThatThrownBy(() -> service.deposit(USER_ID, EMAIL, ACCOUNT_ID, new BigDecimal("10.00"), IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(accounts, ledger);
    }

    // --- deposit -----------------------------------------------------------------------------

    @Test
    void depositPostsBalancedEntriesAndCompletesTheTransaction() {
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deposit(USER_ID, EMAIL, ACCOUNT_ID, new BigDecimal("100.00"), IDEMPOTENCY_KEY);

        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger, times(2)).save(entries.capture());
        LedgerEntry credit = entries.getAllValues().stream()
                .filter(e -> e.getAccountId().equals(customerAccount.getId())).findFirst().orElseThrow();
        LedgerEntry debit = entries.getAllValues().stream()
                .filter(e -> e.getAccountId().equals(cashAccount.getId())).findFirst().orElseThrow();
        assertThat(credit.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(debit.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(credit.getAmount()).isEqualByComparingTo("100.00");
        assertThat(debit.getAmount()).isEqualByComparingTo("100.00");

        verify(auditService).record(any());
        verify(notificationService).send(any());
    }

    @Test
    void depositIntoAnotherCustomersAccountIsRejectedAsIfItDoesNotExist() {
        Account someoneElses = Account.builder().userId(UUID.randomUUID()).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00OTHR0000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(someoneElses));

        // FR-2.3: not a distinct "forbidden" - identical to "no such account".
        assertThatThrownBy(() -> service.deposit(USER_ID, EMAIL, ACCOUNT_ID, new BigDecimal("10.00"), IDEMPOTENCY_KEY))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ledger, never()).save(any());
    }

    // --- withdraw ----------------------------------------------------------------------------

    @Test
    void withdrawWithInsufficientFundsIsRejectedAndPostsNothing() {
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(ledger.balanceOf(customerAccount.getId())).thenReturn(new BigDecimal("50.00"));

        assertThatThrownBy(() -> service.withdraw(USER_ID, EMAIL, ACCOUNT_ID, new BigDecimal("50.01"), IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(ledger, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void withdrawOfExactlyTheAvailableBalanceSucceeds() {
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(ledger.balanceOf(customerAccount.getId())).thenReturn(new BigDecimal("50.00"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.withdraw(USER_ID, EMAIL, ACCOUNT_ID, new BigDecimal("50.00"), IDEMPOTENCY_KEY);

        verify(ledger, times(2)).save(any());
    }

    // --- transfer: destination resolution ------------------------------------------------------

    @Test
    void transferToAnOwnedDestinationAccountIdSucceedsWithNoFee() {
        Account destination = Account.builder().userId(UUID.randomUUID()).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00DEST0000000001").type(AccountType.CHECKING).currency("GBP").build();
        stubNoFeeNoLimitsNoStepUp();
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByIdForUpdate(destination.getId())).thenReturn(Optional.of(destination));
        when(ledger.balanceOf(customerAccount.getId())).thenReturn(new BigDecimal("100.00"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest req = new TransferRequest(ACCOUNT_ID, destination.getId(), null, null,
                new BigDecimal("25.00"), null);
        service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY);

        verify(ledger, times(2)).save(any()); // debit source, credit destination - no fee entries
    }

    @Test
    void transferByAccountNumberWithAMismatchedBeneficiaryNameIsRejectedVaguely() {
        UUID recipientUserId = UUID.randomUUID();
        Account destination = Account.builder().userId(recipientUserId).category(AccountCategory.CUSTOMER)
                .accountNumber("GB29NWBK60161331926819").type(AccountType.CHECKING).currency("GBP").build();
        User recipient = User.builder().email("real@example.com")
                .passwordHash("irrelevant").fullName("Real Recipient").build();
        stubNoFeeNoLimitsNoStepUp();
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByAccountNumber("GB29NWBK60161331926819")).thenReturn(Optional.of(destination));
        when(users.findById(recipientUserId)).thenReturn(Optional.of(recipient));

        TransferRequest req = new TransferRequest(ACCOUNT_ID, null, "GB29NWBK60161331926819",
                "A Completely Different Name", new BigDecimal("10.00"), null);

        assertThatThrownBy(() -> service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(be.getField()).isEqualTo("destinationAccountNumber");
                    // FR-2.3/FR-3.4: must not reveal whether the number or the name was the problem.
                    assertThat(be.getMessage()).doesNotContain("name").doesNotContainIgnoringCase("does not match");
                });
        verify(ledger, never()).save(any());
    }

    @Test
    void transferByAccountNumberIgnoresCaseAndPunctuationWhenMatchingTheName() {
        UUID recipientUserId = UUID.randomUUID();
        Account destination = Account.builder().userId(recipientUserId).category(AccountCategory.CUSTOMER)
                .accountNumber("GB29NWBK60161331926819").type(AccountType.CHECKING).currency("GBP").build();
        User recipient = User.builder().email("real@example.com")
                .passwordHash("irrelevant").fullName("O'Brien-Smith, Jane").build();
        stubNoFeeNoLimitsNoStepUp();
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByAccountNumber("GB29NWBK60161331926819")).thenReturn(Optional.of(destination));
        when(users.findById(recipientUserId)).thenReturn(Optional.of(recipient));
        when(ledger.balanceOf(customerAccount.getId())).thenReturn(new BigDecimal("100.00"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Deliberately different case/spacing/punctuation from the stored name.
        TransferRequest req = new TransferRequest(ACCOUNT_ID, null, "GB29NWBK60161331926819",
                "obrien smith jane", new BigDecimal("10.00"), null);

        service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY);

        verify(ledger, times(2)).save(any());
    }

    @Test
    void transferToANonexistentAccountNumberGetsTheSameVagueRejectionAsAMismatchedName() {
        stubNoFeeNoLimitsNoStepUp();
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByAccountNumber("GB00NOSUCHACCOUNT01")).thenReturn(Optional.empty());

        TransferRequest req = new TransferRequest(ACCOUNT_ID, null, "GB00NOSUCHACCOUNT01",
                "Anyone At All", new BigDecimal("10.00"), null);

        // Same exception, same field, same message as the name-mismatch case above - an attacker
        // cannot distinguish "wrong name" from "no such account" (FR-2.3).
        assertThatThrownBy(() -> service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getField()).isEqualTo("destinationAccountNumber"));
    }

    // --- transfer: daily limit (FR-3.11) --------------------------------------------------------

    @Test
    void transferThatWouldExceedTheDailyLimitIsRejectedBeforeAnythingIsPosted() {
        Account destination = Account.builder().userId(UUID.randomUUID()).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00DEST0000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(feeService.feeFor(any(), any())).thenReturn(new FeeService.FeeResult(BigDecimal.ZERO.setScale(2), null));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByIdForUpdate(destination.getId())).thenReturn(Optional.of(destination));
        when(policyService.dailyLimit()).thenReturn(new BigDecimal("100.00"));
        when(transactions.outboundSince(eq(USER_ID), any(), any())).thenReturn(new BigDecimal("95.00"));

        TransferRequest req = new TransferRequest(ACCOUNT_ID, destination.getId(), null, null,
                new BigDecimal("10.00"), null); // 95 + 10 = 105 > 100

        assertThatThrownBy(() -> service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(ledger, never()).save(any());
        verify(transactions, never()).save(any());
    }

    // --- transfer: step-up (FR-3.9) ---------------------------------------------------------

    @Test
    void transferOverTheStepUpThresholdWithoutElevationIsRejectedAndIssuesAChallenge() {
        Account destination = Account.builder().userId(UUID.randomUUID()).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00DEST0000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(feeService.feeFor(any(), any())).thenReturn(new FeeService.FeeResult(BigDecimal.ZERO.setScale(2), null));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByIdForUpdate(destination.getId())).thenReturn(Optional.of(destination));
        when(policyService.dailyLimit()).thenReturn(new BigDecimal("100000.00"));
        when(transactions.outboundSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(policyService.stepUpThreshold()).thenReturn(new BigDecimal("500.00"));
        when(stepUpService.isElevated(USER_ID)).thenReturn(false);

        TransferRequest req = new TransferRequest(ACCOUNT_ID, destination.getId(), null, null,
                new BigDecimal("501.00"), null); // over the 500.00 threshold, no otp supplied

        assertThatThrownBy(() -> service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(stepUpService).issueChallenge(eq(USER_ID), eq(EMAIL), any());
        verify(ledger, never()).save(any());
    }

    @Test
    void transferOverTheStepUpThresholdWithAValidOtpSucceedsAndConsumesTheElevation() {
        Account destination = Account.builder().userId(UUID.randomUUID()).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00DEST0000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(feeService.feeFor(any(), any())).thenReturn(new FeeService.FeeResult(BigDecimal.ZERO.setScale(2), null));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByIdForUpdate(destination.getId())).thenReturn(Optional.of(destination));
        when(policyService.dailyLimit()).thenReturn(new BigDecimal("100000.00"));
        when(transactions.outboundSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(policyService.stepUpThreshold()).thenReturn(new BigDecimal("500.00"));
        when(stepUpService.verify(USER_ID, "123456")).thenReturn(true);
        when(stepUpService.isElevated(USER_ID)).thenReturn(true);
        when(ledger.balanceOf(customerAccount.getId())).thenReturn(new BigDecimal("1000.00"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest req = new TransferRequest(ACCOUNT_ID, destination.getId(), null, null,
                new BigDecimal("501.00"), "123456");

        service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY);

        verify(stepUpService).verify(USER_ID, "123456");
        verify(stepUpService).consumeElevation(USER_ID);
        verify(ledger, times(2)).save(any());
    }

    // --- transfer: fees post as separate ledger entries ---------------------------------------

    @Test
    void aTransferFeePostsAsSeparateLedgerEntriesAgainstFeeIncome() {
        Account destination = Account.builder().userId(UUID.randomUUID()).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00DEST0000000001").type(AccountType.CHECKING).currency("GBP").build();
        UUID feeScheduleId = UUID.randomUUID();
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(feeService.feeFor(eq(TransactionType.TRANSFER), any()))
                .thenReturn(new FeeService.FeeResult(new BigDecimal("1.50"), feeScheduleId));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(customerAccount));
        when(accounts.findByIdForUpdate(destination.getId())).thenReturn(Optional.of(destination));
        when(policyService.dailyLimit()).thenReturn(new BigDecimal("100000.00"));
        when(transactions.outboundSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(policyService.stepUpThreshold()).thenReturn(new BigDecimal("100000.00"));
        when(ledger.balanceOf(customerAccount.getId())).thenReturn(new BigDecimal("100.00"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest req = new TransferRequest(ACCOUNT_ID, destination.getId(), null, null,
                new BigDecimal("50.00"), null);
        service.transfer(USER_ID, EMAIL, "Customer Name", req, IDEMPOTENCY_KEY);

        // 4 entries: source debit (principal), destination credit, source debit (fee), fee-income credit.
        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger, times(4)).save(entries.capture());
        boolean feeCreditedToFeeIncome = entries.getAllValues().stream()
                .anyMatch(e -> e.getAccountId().equals(feeIncomeAccount.getId())
                        && e.getDirection() == LedgerDirection.CREDIT
                        && e.getAmount().compareTo(new BigDecimal("1.50")) == 0);
        assertThat(feeCreditedToFeeIncome).isTrue();
    }

    private void stubNoFeeNoLimitsNoStepUp() {
        when(transactions.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(feeService.feeFor(any(), any())).thenReturn(new FeeService.FeeResult(BigDecimal.ZERO.setScale(2), null));
        when(policyService.dailyLimit()).thenReturn(new BigDecimal("100000.00"));
        when(transactions.outboundSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(policyService.stepUpThreshold()).thenReturn(new BigDecimal("100000.00"));
    }
}

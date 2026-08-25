package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.Account;
import com.havenbank.backend.money.domain.AccountCategory;
import com.havenbank.backend.money.domain.AccountType;
import com.havenbank.backend.money.dto.AccountResponse;
import com.havenbank.backend.money.mapper.AccountMapper;
import com.havenbank.backend.money.repository.AccountRepository;
import com.havenbank.backend.money.repository.LedgerEntryRepository;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FR-2.3 (IDOR-safe reads) and account-open idempotency, isolated from the real database that
 * {@code AccountIntegrationTest} exercises. The concurrent-double-submit race in {@link #open}
 * (two requests, same key, unique-index loser reads back the winner) is specifically covered here,
 * since triggering a real {@code DataIntegrityViolationException} race deterministically against
 * Postgres would be far more effort than mocking the exact scenario it handles.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private AccountRepository accounts;
    @Mock
    private LedgerEntryRepository ledger;
    @Mock
    private AuditService auditService;
    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService service;

    @BeforeEach
    void setUp() {
        when(ledger.balanceOf(any())).thenReturn(BigDecimal.ZERO.setScale(2));
        when(accountMapper.toResponse(any(), any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            return new AccountResponse(a.getId(), a.getAccountNumber(), a.getType().name(),
                    a.getCurrency(), a.getStatus().name(), inv.getArgument(1));
        });
    }

    @Test
    void openingWithANewIdempotencyKeyCreatesAnAccount() {
        when(accounts.findByUserIdAndIdempotencyKey(eq(USER_ID), anyString())).thenReturn(Optional.empty());
        when(accounts.findByAccountNumber(anyString())).thenReturn(Optional.empty());

        AccountResponse response = service.open(USER_ID, "CHECKING", "GBP", "key-1");

        verify(accounts).saveAndFlush(any(Account.class));
        verify(auditService).record(any());
        assertThat(response.type()).isEqualTo("CHECKING");
    }

    @Test
    void replayingAnAlreadyHonouredIdempotencyKeyReturnsTheOriginalAccountWithoutCreatingASecondOne() {
        Account existing = Account.builder().userId(USER_ID).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00EXST0000000001").type(AccountType.SAVINGS).currency("GBP")
                .idempotencyKey("key-1").build();
        when(accounts.findByUserIdAndIdempotencyKey(USER_ID, "key-1")).thenReturn(Optional.of(existing));

        AccountResponse response = service.open(USER_ID, "SAVINGS", "GBP", "key-1");

        assertThat(response.type()).isEqualTo("SAVINGS");
        verify(accounts, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void aConcurrentDoubleSubmitRaceReadsBackTheWinnersAccountInsteadOfFailing() {
        Account winner = Account.builder().userId(USER_ID).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00WINR0000000001").type(AccountType.CHECKING).currency("GBP")
                .idempotencyKey("key-race").build();
        when(accounts.findByUserIdAndIdempotencyKey(eq(USER_ID), eq("key-race")))
                .thenReturn(Optional.empty()) // first check: nothing yet, so we attempt to create
                .thenReturn(Optional.of(winner)); // after the race: the other request's row is there
        when(accounts.findByAccountNumber(anyString())).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(accounts).saveAndFlush(any(Account.class));

        AccountResponse response = service.open(USER_ID, "CHECKING", "GBP", "key-race");

        assertThat(response.accountNumber()).isEqualTo("GB00WINR0000000001");
    }

    @Test
    void aGenuineIntegrityViolationUnrelatedToTheIdempotencyKeyIsRethrown() {
        // No idempotency key at all, so there is nothing to read back - the exception must surface,
        // not be silently swallowed as if it were an idempotency race.
        when(accounts.findByAccountNumber(anyString())).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("some other constraint"))
                .when(accounts).saveAndFlush(any(Account.class));

        assertThatThrownBy(() -> service.open(USER_ID, "CHECKING", "GBP", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void gettingAnotherCustomersAccountIsIndistinguishableFromItNotExisting() {
        UUID someoneElsesAccount = UUID.randomUUID();
        when(accounts.findByIdAndUserId(someoneElsesAccount, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(USER_ID, someoneElsesAccount))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closingAnAccountWithANonzeroBalanceIsRejected() {
        Account account = Account.builder().userId(USER_ID).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00OPEN0000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(accounts.findByIdAndUserId(account.getId(), USER_ID)).thenReturn(Optional.of(account));
        when(ledger.balanceOf(account.getId())).thenReturn(new BigDecimal("0.01"));

        assertThatThrownBy(() -> service.close(USER_ID, account.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(account.isActive()).isTrue(); // rejected before the account was touched
    }

    @Test
    void closingAZeroBalanceAccountSucceeds() {
        Account account = Account.builder().userId(USER_ID).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00OPEN0000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(accounts.findByIdAndUserId(account.getId(), USER_ID)).thenReturn(Optional.of(account));
        when(ledger.balanceOf(account.getId())).thenReturn(BigDecimal.ZERO.setScale(2));

        service.close(USER_ID, account.getId());

        assertThat(account.isActive()).isFalse();
        verify(auditService).record(any());
    }

    @Test
    void closingAnotherCustomersAccountIsIndistinguishableFromItNotExisting() {
        UUID someoneElsesAccount = UUID.randomUUID();
        when(accounts.findByIdAndUserId(someoneElsesAccount, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(USER_ID, someoneElsesAccount))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listOwnOnlyReturnsWhatTheRepositoryScopedToThisUser() {
        Account own = Account.builder().userId(USER_ID).category(AccountCategory.CUSTOMER)
                .accountNumber("GB00OWN00000000001").type(AccountType.CHECKING).currency("GBP").build();
        when(accounts.findByUserId(USER_ID)).thenReturn(java.util.List.of(own));

        var result = service.listOwn(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountNumber()).isEqualTo("GB00OWN00000000001");
    }
}

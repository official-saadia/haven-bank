package com.havenbank.backend.money.mapper;

import com.havenbank.backend.money.domain.Account;
import com.havenbank.backend.money.dto.AccountResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Maps {@link Account} to {@link AccountResponse}. The balance is ledger-derived and therefore
 * passed in by the caller: the mapper never reaches into the ledger itself, which keeps it free of
 * collaborators and avoids hiding an N+1 when mapping a list of accounts.
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account, BigDecimal balance) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getType().name(),
                account.getCurrency(), account.getStatus().name(), balance);
    }
}

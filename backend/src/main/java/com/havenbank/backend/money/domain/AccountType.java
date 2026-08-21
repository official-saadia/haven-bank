package com.havenbank.backend.money.domain;

/**
 * Kind of account. Customer-facing types plus bank-internal contra accounts for the ledger.
 */
public enum AccountType {
    CHECKING,
    SAVINGS,
    /**
     * Bank-internal clearing/cash account (counterparty for deposits and withdrawals).
     */
    CASH,
    /**
     * Bank-internal account that receives transfer fees.
     */
    FEE_INCOME
}

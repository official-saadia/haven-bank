package com.havenbank.backend.money.domain;

/**
 * Outcome of a money movement. Only COMPLETED movements have committed ledger entries.
 */
public enum TransactionStatus {PENDING, COMPLETED, FAILED}

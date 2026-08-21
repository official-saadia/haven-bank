package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull(message = "Enter an amount to deposit.")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01.")
        @Digits(integer = 17, fraction = 2, message = "Amount can have at most two decimal places.")
        BigDecimal amount
) {
}
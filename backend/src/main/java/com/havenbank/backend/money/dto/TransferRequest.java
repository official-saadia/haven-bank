package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A transfer from an owned source account to either another account number (third party) or an
 * owned destination account id. Exactly one destination form should be provided; which one is
 * present, and cross-field rules like name-required-for-third-party, are resolved in the service.
 */
public record TransferRequest(
        @NotNull(message = "Choose an account to send from.") UUID sourceAccountId,

        UUID destinationAccountId,

        @Pattern(regexp = "|[A-Za-z0-9-]{6,34}",
                message = "Account number must be 6-34 letters, digits or hyphens.")
        String destinationAccountNumber,

        @Size(max = 140) String beneficiaryName,

        @NotNull(message = "Enter an amount to transfer.")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01.")
        @Digits(integer = 17, fraction = 2, message = "Amount can have at most two decimal places.")
        BigDecimal amount,

        @Pattern(regexp = "|\\d{6}", message = "The code is 6 digits.") String otp
) {
}
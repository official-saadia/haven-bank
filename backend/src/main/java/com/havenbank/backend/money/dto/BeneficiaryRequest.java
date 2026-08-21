package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add or update a saved payee. The account number is shape-validated only — whether it actually
 * exists is deliberately not checked here (see {@code BeneficiaryService}).
 *
 * <p>The name rule is looser than the account holder's own (RegisterRequest): a payee may be a
 * business, so digits are allowed ("7-Eleven", "Studio 54") as long as a letter is present — which
 * still rejects a bare number like "1212121".
 */
public record BeneficiaryRequest(
        @NotBlank(message = "Enter the beneficiary's name.")
        @Size(max = 140)
        @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M}\\p{N} .,'&/'-]+",
                message = "Enter a valid name.")
        String name,

        @Size(max = 60, message = "Nickname is too long.") String nickname,

        @NotBlank(message = "Enter an account number.")
        @Pattern(regexp = "[A-Za-z0-9-]{6,34}",
                message = "Account number must be 6-34 letters, digits or hyphens.")
        String accountNumber
) {
}
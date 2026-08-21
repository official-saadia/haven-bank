package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Opens an account of a given type and currency for the authenticated customer.
 */
public record OpenAccountRequest(
        @NotBlank(message = "Choose an account type.")
        @Pattern(regexp = "CHECKING|SAVINGS", message = "Account type must be CHECKING or SAVINGS.")
        String type,

        @NotBlank(message = "Choose a currency.")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO code, e.g. GBP.")
        String currency
) {
}
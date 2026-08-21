package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Admin policy configuration (limits, thresholds). The value's meaning depends on the key, but no
 * policy value is negative, so that floor is enforced for all of them.
 */
public record PolicyRequest(
        @NotBlank(message = "Specify which policy to set.") String policyKey,
        @NotBlank(message = "Specify the policy scope.") String scope,
        @NotNull(message = "Enter a value.")
        @DecimalMin(value = "0.00", message = "Value can't be negative.")
        @Digits(integer = 17, fraction = 4)
        BigDecimal value
) {
}
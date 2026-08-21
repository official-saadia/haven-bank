package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Admin fee-schedule configuration. Amounts were previously unbounded — a negative flat fee or a
 * percentage over 100 would have been accepted and silently applied to real transfers. The bounds
 * are enforced here; the tierMin ≤ tierMax cross-field rule is checked in the service.
 */
public record FeeScheduleRequest(
        @NotNull(message = "Specify what this fee applies to.")
        @Pattern(regexp = "\\S.*", message = "Specify what this fee applies to.")
        String appliesTo,

        @DecimalMin(value = "0.00", message = "Tier minimum can't be negative.")
        @Digits(integer = 17, fraction = 2)
        BigDecimal tierMin,

        @DecimalMin(value = "0.00", message = "Tier maximum can't be negative.")
        @Digits(integer = 17, fraction = 2)
        BigDecimal tierMax,

        @NotNull(message = "Enter a flat fee (0 for none).")
        @DecimalMin(value = "0.00", message = "Flat fee can't be negative.")
        @Digits(integer = 17, fraction = 2)
        BigDecimal feeFlat,

        @NotNull(message = "Enter a percentage fee (0 for none).")
        @DecimalMin(value = "0.00", message = "Percentage fee can't be negative.")
        @DecimalMax(value = "100.00", message = "Percentage fee can't exceed 100%.")
        @Digits(integer = 3, fraction = 4)
        BigDecimal feePercent
) {
}
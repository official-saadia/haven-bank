package com.havenbank.backend.money.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRequest(
        @NotBlank(message = "Enter the code from your email.")
        @Pattern(regexp = "\\d{6}", message = "The code is 6 digits.") String code) {
}
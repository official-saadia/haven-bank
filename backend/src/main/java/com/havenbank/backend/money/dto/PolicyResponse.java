package com.havenbank.backend.money.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PolicyResponse(UUID id, String policyKey, String scope, BigDecimal value,
                             Instant effectiveFrom, Instant effectiveTo) {
}

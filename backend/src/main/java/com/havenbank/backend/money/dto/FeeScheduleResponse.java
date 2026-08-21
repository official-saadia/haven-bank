package com.havenbank.backend.money.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FeeScheduleResponse(UUID id, String appliesTo, BigDecimal tierMin, BigDecimal tierMax,
                                  BigDecimal feeFlat, BigDecimal feePercent,
                                  Instant effectiveFrom, Instant effectiveTo) {
}

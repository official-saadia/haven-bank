package com.havenbank.backend.money.service;

import com.havenbank.backend.money.domain.FeeSchedule;
import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.repository.FeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Computes the fee for a movement using the effective {@link FeeSchedule} version.
 */
@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeScheduleRepository feeSchedules;

    public record FeeResult(BigDecimal amount, UUID scheduleId) {
    }

    public FeeResult feeFor(TransactionType type, BigDecimal amount) {
        List<FeeSchedule> effective = feeSchedules.findEffective(type, amount, Instant.now());
        if (effective.isEmpty()) {
            return new FeeResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN), null);
        }
        FeeSchedule f = effective.get(0);
        BigDecimal fee = f.getFeeFlat()
                .add(amount.multiply(f.getFeePercent()))
                .setScale(2, RoundingMode.HALF_EVEN);
        return new FeeResult(fee, f.getId());
    }
}

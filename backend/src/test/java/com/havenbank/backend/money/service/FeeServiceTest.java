package com.havenbank.backend.money.service;

import com.havenbank.backend.money.domain.FeeSchedule;
import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.repository.FeeScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Fees come from the effective versioned schedule, in fixed-precision decimal (FR-3.6).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeServiceTest {

    @Mock
    private FeeScheduleRepository feeSchedules;
    @InjectMocks
    private FeeService feeService;

    private FeeSchedule schedule(String flat, String percent, UUID id) {
        FeeSchedule s = org.mockito.Mockito.mock(FeeSchedule.class);
        when(s.getFeeFlat()).thenReturn(new BigDecimal(flat));
        when(s.getFeePercent()).thenReturn(new BigDecimal(percent));
        when(s.getId()).thenReturn(id);
        return s;
    }

    @Test
    void chargesNothingWhenNoScheduleIsEffective() {
        when(feeSchedules.findEffective(any(), any(), any(Instant.class))).thenReturn(List.of());

        FeeService.FeeResult result = feeService.feeFor(TransactionType.TRANSFER, new BigDecimal("100.00"));

        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result.scheduleId()).isNull();
    }

    @Test
    void combinesTheFlatAndPercentageComponents() {
        UUID id = UUID.randomUUID();
        when(feeSchedules.findEffective(eq(TransactionType.TRANSFER), any(), any(Instant.class)))
                .thenReturn(List.of(schedule("1.50", "0.01", id)));

        FeeService.FeeResult result = feeService.feeFor(TransactionType.TRANSFER, new BigDecimal("200.00"));

        // 1.50 flat + 1% of 200.00
        assertThat(result.amount()).isEqualByComparingTo("3.50");
        assertThat(result.scheduleId()).isEqualTo(id);
    }

    @Test
    void roundsToTwoDecimalPlacesHalfEven() {
        when(feeSchedules.findEffective(any(), any(), any(Instant.class)))
                .thenReturn(List.of(schedule("0.00", "0.033", UUID.randomUUID())));

        // 0.033 * 10.10 = 0.3333 -> 0.33
        FeeService.FeeResult result = feeService.feeFor(TransactionType.TRANSFER, new BigDecimal("10.10"));

        assertThat(result.amount()).isEqualByComparingTo("0.33");
        assertThat(result.amount().scale()).isEqualTo(2);
    }

    @Test
    void usesTheFirstEffectiveVersionWhenSeveralMatch() {
        UUID current = UUID.randomUUID();
        when(feeSchedules.findEffective(any(), any(), any(Instant.class)))
                .thenReturn(List.of(schedule("2.00", "0.00", current),
                        schedule("9.99", "0.50", UUID.randomUUID())));

        FeeService.FeeResult result = feeService.feeFor(TransactionType.WITHDRAWAL, new BigDecimal("50.00"));

        assertThat(result.amount()).isEqualByComparingTo("2.00");
        assertThat(result.scheduleId()).isEqualTo(current);
    }
}

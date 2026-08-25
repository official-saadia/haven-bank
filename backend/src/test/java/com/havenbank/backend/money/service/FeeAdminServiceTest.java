package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.FeeSchedule;
import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.dto.FeeScheduleRequest;
import com.havenbank.backend.money.dto.FeeScheduleResponse;
import com.havenbank.backend.money.mapper.FeeScheduleMapper;
import com.havenbank.backend.money.repository.FeeScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FeeAdminService's versioning: creating a new fee schedule closes whichever row was previously
 * open for that transaction type, rather than leaving two simultaneously-effective rows -
 * {@code FeeService.feeFor} takes the first "effective" match, so a stale open row would silently
 * shadow the new one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeAdminServiceTest {

    @Mock
    private FeeScheduleRepository feeSchedules;
    @Mock
    private AuditService auditService;
    @Mock
    private FeeScheduleMapper feeScheduleMapper;

    @InjectMocks
    private FeeAdminService service;

    @Test
    void creatingANewVersionClosesTheCurrentlyOpenOneForTheSameType() {
        FeeSchedule currentlyOpen = mock(FeeSchedule.class);
        when(feeSchedules.findByAppliesToAndEffectiveToIsNull(TransactionType.TRANSFER))
                .thenReturn(List.of(currentlyOpen));
        when(feeSchedules.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(feeScheduleMapper.toResponse(any())).thenReturn(
                new FeeScheduleResponse(UUID.randomUUID(), "TRANSFER", null, null,
                        new BigDecimal("1.50"), new BigDecimal("0.00"), null, null));

        service.createVersion(UUID.randomUUID(), new FeeScheduleRequest("TRANSFER", null, null,
                new BigDecimal("1.50"), BigDecimal.ZERO));

        verify(currentlyOpen).close(any());
        verify(feeSchedules).save(any());
        verify(auditService).record(any());
    }

    @Test
    void creatingTheFirstVersionForATypeWithNoExistingRowsJustCreatesOne() {
        when(feeSchedules.findByAppliesToAndEffectiveToIsNull(TransactionType.DEPOSIT))
                .thenReturn(List.of());
        when(feeSchedules.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(feeScheduleMapper.toResponse(any())).thenReturn(
                new FeeScheduleResponse(UUID.randomUUID(), "DEPOSIT", null, null,
                        BigDecimal.ZERO, BigDecimal.ZERO, null, null));

        service.createVersion(UUID.randomUUID(), new FeeScheduleRequest("DEPOSIT", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO));

        verify(feeSchedules).save(any());
    }
}

package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.FeeSchedule;
import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.dto.FeeScheduleRequest;
import com.havenbank.backend.money.dto.FeeScheduleResponse;
import com.havenbank.backend.money.repository.FeeScheduleRepository;
import com.havenbank.backend.money.mapper.FeeScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Versioned fee administration (ADMIN). A new rate closes the current one and inserts a new row.
 */
@Service
@RequiredArgsConstructor
public class FeeAdminService {

    private final FeeScheduleRepository feeSchedules;
    private final AuditService auditService;
    private final FeeScheduleMapper feeScheduleMapper;

    @Transactional(readOnly = true)
    public List<FeeScheduleResponse> list() {
        return feeSchedules.findAll().stream().map(feeScheduleMapper::toResponse).toList();
    }

    @Transactional
    public FeeScheduleResponse createVersion(UUID actor, FeeScheduleRequest request) {
        TransactionType type = TransactionType.valueOf(request.appliesTo());
        Instant now = Instant.now();
        feeSchedules.findByAppliesToAndEffectiveToIsNull(type).forEach(f -> f.close(now));

        FeeSchedule created = feeSchedules.save(FeeSchedule.builder()
                .appliesTo(type).tierMin(request.tierMin()).tierMax(request.tierMax())
                .feeFlat(request.feeFlat()).feePercent(request.feePercent()).effectiveFrom(now).build());
        auditService.record(AuditEvent.success(actor, AuditAction.FEE_SCHEDULE_VERSIONED, type.name()));
        return feeScheduleMapper.toResponse(created);
    }

}

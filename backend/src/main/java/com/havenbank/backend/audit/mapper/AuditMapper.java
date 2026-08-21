package com.havenbank.backend.audit.mapper;

import com.havenbank.backend.audit.domain.AuditTrail;
import com.havenbank.backend.audit.dto.AuditRecordView;
import org.springframework.stereotype.Component;

/**
 * Maps {@link AuditTrail} to {@link AuditRecordView}. The human-readable {@code actor} label is
 * resolved by the caller (it may require a user lookup) and passed in as context.
 */
@Component
public class AuditMapper {

    public AuditRecordView toView(AuditTrail a, String actor) {
        return new AuditRecordView(a.getId(), a.getActorUserId(), actor, a.getAction(), a.getTargetType(),
                a.getTargetId(), a.getOutcome(), a.getDetail(), a.getSourceIp(), a.getUserAgent(),
                a.getCorrelationId(), a.getCreatedAt());
    }
}

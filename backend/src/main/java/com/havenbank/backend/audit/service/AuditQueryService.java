package com.havenbank.backend.audit.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.domain.AuditTrail;
import com.havenbank.backend.audit.dto.AuditRecordView;
import com.havenbank.backend.audit.mapper.AuditMapper;
import com.havenbank.backend.audit.repository.AuditTrailRepository;
import com.havenbank.backend.iam.service.UserDirectory;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-side queries over the audit trail. Owns the trail lookup, the actor-id &rarr; email
 * resolution used to render a human-readable actor, and the not-found decision, so
 * {@code AuditQueryController} stays thin and consistent with every other read controller: the
 * controller maps HTTP, the service holds the logic and throws {@link ResourceNotFoundException}.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditTrailRepository repository;
    private final AuditService auditService;
    private final UserDirectory userDirectory;
    private final AuditMapper auditMapper;

    /**
     * Paginated, optionally action-filtered trail, newest first. Viewing the trail is itself an
     * audited event (reads of the audit log are audited), which is why this is not a pure read.
     */
    @Transactional
    public Page<AuditRecordView> list(UUID viewerId, AuditAction action, Pageable pageable) {
        Page<AuditTrail> result = (action == null)
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByActionOrderByCreatedAtDesc(action, pageable);

        auditService.record(AuditEvent.success(viewerId, AuditAction.AUDIT_VIEWED, "audit list viewed"));

        Set<UUID> actorIds = result.getContent().stream()
                .map(AuditTrail::getActorUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> emails = userDirectory.emailsByIds(actorIds);
        return result.map(a -> auditMapper.toView(a, actorLabel(a, emails)));
    }

    /**
     * A single record by id, or {@link ResourceNotFoundException} (&rarr; 404) if absent.
     */
    @Transactional(readOnly = true)
    public AuditRecordView get(UUID id) {
        AuditTrail record = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found"));
        Map<UUID, String> emails = record.getActorUserId() == null
                ? Map.of()
                : userDirectory.emailsByIds(Set.of(record.getActorUserId()));
        return auditMapper.toView(record, actorLabel(record, emails));
    }

    /**
     * Resolve a human-readable actor: the acting user's email for authenticated actions; the
     * attempted identity for principal-less events (a failed login records it as its target); and a
     * plain {@code "system"} for anonymous/system actions with neither. Falls back to the raw id if
     * the acting user has since been deleted, so the row is never silently blank.
     */
    private static String actorLabel(AuditTrail a, Map<UUID, String> emails) {
        if (a.getActorUserId() != null) {
            return emails.getOrDefault(a.getActorUserId(), a.getActorUserId().toString());
        }
        if ("User".equals(a.getTargetType()) && a.getTargetId() != null) {
            return a.getTargetId();
        }
        return "system";
    }
}
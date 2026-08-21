package com.havenbank.backend.audit.controller;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.domain.AuditTrail;
import com.havenbank.backend.audit.dto.AuditRecordView;
import com.havenbank.backend.audit.repository.AuditTrailRepository;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.service.UserDirectory;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only audit access for Staff/Admin. The trail is written by the system
 */
@Tag(name = "Admin: audit", description = "Read the security audit log. Requires the AUDIT_READ permission (STAFF or ADMIN).")
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasAuthority('AUDIT_READ')")
@RequiredArgsConstructor
class AuditQueryController {

    private final AuditTrailRepository repository;
    private final AuditService auditService;
    private final UserDirectory userDirectory;

    @Operation(summary = "List audit records", description = "Paginated, filterable security audit log, newest first.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping
    public Page<AuditRecordView> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) AuditAction action,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200));
        Page<AuditTrail> result = (action == null)
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByActionOrderByCreatedAtDesc(action, pageable);
        auditService.record(AuditEvent.success(UUID.fromString(jwt.getSubject()),
                AuditAction.AUDIT_VIEWED, "audit list viewed"));

        Set<UUID> actorIds = result.getContent().stream()
                .map(AuditTrail::getActorUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> emails = userDirectory.emailsByIds(actorIds);
        return result.map(a -> AuditRecordView.of(a, actorLabel(a, emails)));
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

    @Operation(summary = "Get an audit record", description = "Fetches a single audit record by id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The audit record"),
            @ApiResponse(responseCode = "404", description = "No such record", content = @Content())})
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/{id}")
    public AuditRecordView get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(a -> AuditRecordView.of(a, actorLabel(a,
                        a.getActorUserId() == null ? Map.of()
                                : userDirectory.emailsByIds(Set.of(a.getActorUserId())))))
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found"));
    }
}
package com.havenbank.backend.notification.controller;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.notification.domain.NotificationCategory;
import com.havenbank.backend.notification.domain.NotificationPreference;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.dto.PreferenceView;
import com.havenbank.backend.notification.dto.UpdateRequest;
import com.havenbank.backend.notification.repository.NotificationPreferenceRepository;
import com.havenbank.backend.notification.service.PreferenceGate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Self-service notification preferences. Only convenience categories are listed and editable;
 * security-critical types are omitted because they are mandatory (FR-7.1b).
 */
@Tag(name = "Notification preferences", description = "Per-category notification opt-ins. " +
        "Security-critical notifications are non-suppressible (FR-7.1b).")
@RestController
@RequestMapping("/api/v1/me/notification-preferences")
@RequiredArgsConstructor
class NotificationPreferenceController {

    private final NotificationPreferenceRepository preferences;
    private final AuditService auditService;

    @Operation(summary = "Get my notification preferences", description = "Returns the caller's notification preferences by category.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping
    @Transactional(readOnly = true)
    public List<PreferenceView> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return Arrays.stream(NotificationType.values())
                .filter(t -> t.category() == NotificationCategory.CONVENIENCE)
                .map(t -> new PreferenceView(t.name(),
                        preferences.findByUserIdAndTypeAndChannel(userId, t, PreferenceGate.EMAIL)
                                .map(NotificationPreference::isEnabled).orElse(true)))
                .toList();
    }

    @Operation(summary = "Update my notification preferences", description = "Updates convenience-notification opt-ins." +
            " Security-critical categories cannot be disabled.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated preferences"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content())})
    @RateLimited(RateLimitTier.SENSITIVE)
    @PutMapping
    @Transactional
    public List<PreferenceView> update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        for (PreferenceView pv : request.preferences()) {
            NotificationType type = NotificationType.valueOf(pv.type());
            if (type.category() != NotificationCategory.CONVENIENCE) {
                continue; // security-critical types cannot be disabled - ignore silently
            }
            NotificationPreference pref = preferences
                    .findByUserIdAndTypeAndChannel(userId, type, PreferenceGate.EMAIL)
                    .orElseGet(() -> new NotificationPreference(userId, type, PreferenceGate.EMAIL, true));
            pref.setEnabled(pv.enabled());
            preferences.save(pref);
        }
        auditService.record(AuditEvent.success(userId, AuditAction.NOTIFICATION_PREFS_UPDATED, "updated"));
        return list(jwt);
    }
}
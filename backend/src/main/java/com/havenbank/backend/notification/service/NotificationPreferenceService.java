package com.havenbank.backend.notification.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.notification.domain.NotificationCategory;
import com.havenbank.backend.notification.domain.NotificationPreference;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.dto.PreferenceView;
import com.havenbank.backend.notification.dto.UpdateRequest;
import com.havenbank.backend.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Self-service notification preferences. Only convenience categories are listed and editable;
 * security-critical types are mandatory and non-suppressible (FR-7.1b). Owns the store access, the
 * find-or-create upsert, the convenience-only policy and the audit write, so the controller only
 * maps HTTP.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferences;
    private final AuditService auditService;

    /**
     * The caller's convenience preferences, one per convenience {@link NotificationType}. A type the
     * customer has never touched defaults to enabled (opt-out model).
     */
    @Transactional(readOnly = true)
    public List<PreferenceView> list(UUID userId) {
        return Arrays.stream(NotificationType.values())
                .filter(t -> t.category() == NotificationCategory.CONVENIENCE)
                .map(t -> new PreferenceView(t.name(),
                        preferences.findByUserIdAndTypeAndChannel(userId, t, PreferenceGate.EMAIL)
                                .map(NotificationPreference::isEnabled).orElse(true)))
                .toList();
    }

    /**
     * Apply convenience opt-ins. Security-critical types in the request are ignored silently rather
     * than rejected, so a client that submits the full set can't accidentally 400 on a mandatory one.
     */
    @Transactional
    public List<PreferenceView> update(UUID userId, UpdateRequest request) {
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
        return list(userId);
    }
}
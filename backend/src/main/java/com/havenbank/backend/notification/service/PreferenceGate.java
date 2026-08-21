package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.domain.NotificationCategory;
import com.havenbank.backend.notification.domain.NotificationPreference;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Decides whether a notification may be delivered given the user's preferences.
 */
@Service
@RequiredArgsConstructor
public class PreferenceGate {

    public static final String EMAIL = "EMAIL";

    private final NotificationPreferenceRepository preferences;

    @Transactional(readOnly = true)
    boolean isAllowed(UUID userId, NotificationType type, String channel) {
        // Security-critical notifications are always delivered; convenience defaults to enabled.
        if (type.category() == NotificationCategory.SECURITY_CRITICAL || userId == null) {
            return true;
        }
        return preferences.findByUserIdAndTypeAndChannel(userId, type, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }
}

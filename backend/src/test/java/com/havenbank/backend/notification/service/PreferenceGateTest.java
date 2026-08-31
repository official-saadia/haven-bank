package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.domain.NotificationPreference;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Security-critical notifications are non-suppressible; convenience ones are opt-out and default
 * to on (FR-7.1a, FR-7.1b).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreferenceGateTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private NotificationPreferenceRepository preferences;
    @InjectMocks
    private PreferenceGate gate;

    private NotificationPreference preference(boolean enabled) {
        NotificationPreference p = org.mockito.Mockito.mock(NotificationPreference.class);
        when(p.isEnabled()).thenReturn(enabled);
        return p;
    }


    @Test
    void deliversWhenThereIsNoUserToHavePreferences() {
        assertThat(gate.isAllowed(null, NotificationType.ACCOUNT_CREATED, PreferenceGate.EMAIL)).isTrue();
    }

    @Test
    void everySecurityCriticalTypeIsNonSuppressible() {
        for (NotificationType type : NotificationType.values()) {
            if (type.category() == com.havenbank.backend.notification.domain.NotificationCategory.SECURITY_CRITICAL) {
                assertThat(gate.isAllowed(USER, type, PreferenceGate.EMAIL))
                        .as("%s must not be suppressible", type)
                        .isTrue();
            }
        }
    }

    @Test
    void alwaysDeliversSecurityCriticalNotificationsEvenIfOptedOut() {
        // Build and finish stubbing the nested mock BEFORE opening the outer when() - see
        // FeeServiceTest for the full explanation of why nesting a preference(...) call (itself a
        // when()...thenReturn()) inside the outer .thenReturn(...) argument corrupts Mockito's
        // single-slot stubbing-progress tracker.
        NotificationPreference optedOut = preference(false);
        when(preferences.findByUserIdAndTypeAndChannel(any(), any(), any()))
                .thenReturn(Optional.of(optedOut));

        assertThat(gate.isAllowed(USER, NotificationType.PASSWORD_CHANGED, PreferenceGate.EMAIL)).isTrue();
        // The preference store is not even consulted for these.
        verify(preferences, never()).findByUserIdAndTypeAndChannel(any(), any(), any());
    }

    @Test
    void deliversConvenienceNotificationsByDefaultWhenNoPreferenceIsStored() {
        when(preferences.findByUserIdAndTypeAndChannel(eq(USER), eq(NotificationType.MONEY_MOVEMENT), eq(PreferenceGate.EMAIL)))
                .thenReturn(Optional.empty());

        assertThat(gate.isAllowed(USER, NotificationType.MONEY_MOVEMENT, PreferenceGate.EMAIL)).isTrue();
    }

    @Test
    void suppressesConvenienceNotificationsWhenTheUserHasOptedOut() {
        NotificationPreference optedOut = preference(false);
        when(preferences.findByUserIdAndTypeAndChannel(eq(USER), eq(NotificationType.MONEY_MOVEMENT), eq(PreferenceGate.EMAIL)))
                .thenReturn(Optional.of(optedOut));

        assertThat(gate.isAllowed(USER, NotificationType.MONEY_MOVEMENT, PreferenceGate.EMAIL)).isFalse();
    }

    @Test
    void deliversConvenienceNotificationsWhenExplicitlyEnabled() {
        NotificationPreference optedIn = preference(true);
        when(preferences.findByUserIdAndTypeAndChannel(eq(USER), eq(NotificationType.MONEY_MOVEMENT), eq(PreferenceGate.EMAIL)))
                .thenReturn(Optional.of(optedIn));

        assertThat(gate.isAllowed(USER, NotificationType.MONEY_MOVEMENT, PreferenceGate.EMAIL)).isTrue();
    }
}

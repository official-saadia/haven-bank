package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.domain.Status;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FR-7.4 (delivery never breaks the caller) and FR-5.3/FR-1.8b applied to the retry outbox
 * specifically: a failed delivery is only re-queued with its rendered content for types that carry
 * no secret. An OTP or verification-link failure must be recorded without ever persisting the code
 * or link, since the outbox row is exactly the kind of place a secret must never land.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailNotificationServiceTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private PreferenceGate preferenceGate;
    @Mock
    private NotificationRepository notifications;

    private EmailNotificationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new EmailNotificationService(emailSender, preferenceGate, notifications);
        when(preferenceGate.isAllowed(any(), any(), any())).thenReturn(true);
    }

    @Test
    void aSuppressedConveniencePreferenceIsRecordedAndNeverSent() {
        when(preferenceGate.isAllowed(any(), eq(NotificationType.ACCOUNT_CREATED), eq(PreferenceGate.EMAIL)))
                .thenReturn(false);
        NotificationMessage message = new NotificationMessage("customer@example.com", "Customer",
                UUID.randomUUID(), NotificationType.ACCOUNT_CREATED, Map.of());

        service.send(message);

        verifyNoInteractions(emailSender);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.SUPPRESSED);
    }

    @Test
    void aSuccessfulSendIsRecordedSent() {
        NotificationMessage message = new NotificationMessage("customer@example.com", "Customer",
                UUID.randomUUID(), NotificationType.ACCOUNT_CREATED, Map.of());

        service.send(message);

        verify(emailSender).send(eq("customer@example.com"), anyString(), anyString());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.SENT);
    }

    @Test
    void aFailedRetryableNotificationIsEnqueuedForRetryWithItsRenderedContent() {
        doThrow(new RuntimeException("smtp timeout")).when(emailSender).send(any(), any(), any());
        NotificationMessage message = new NotificationMessage("customer@example.com", "Customer",
                UUID.randomUUID(), NotificationType.ACCOUNT_CREATED, Map.of()); // ACCOUNT_CREATED is retryable

        service.send(message);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        Notification saved = captor.getValue();
        // recordFailure(...) with maxAttempts=3 on the first failure moves to PENDING, not
        // DEAD_LETTER yet, and the rendered subject/body must be present for the retry worker.
        assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
        assertThat(saved.getSubject()).isNotBlank();
        assertThat(saved.getBody()).isNotBlank();
    }

    @Test
    void aFailedSecretBearingNotificationIsRecordedFailedWithoutStoringTheSecretContent() {
        doThrow(new RuntimeException("smtp timeout")).when(emailSender).send(any(), any(), any());
        NotificationMessage message = new NotificationMessage("customer@example.com", "Customer",
                UUID.randomUUID(), NotificationType.LOGIN_OTP, Map.of("code", "123456"));

        service.send(message);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        Notification saved = captor.getValue();
        // The actual security-critical assertion: LOGIN_OTP is not retryable (carries the code), so
        // it must be recorded via Notification.of(...FAILED), which never stores subject/body at
        // all - the "123456" code must never land in a persisted row.
        assertThat(saved.getStatus()).isEqualTo(Status.FAILED);
        assertThat(saved.getSubject()).isNull();
        assertThat(saved.getBody()).isNull();
    }

    @Test
    void everyRetryableTypeOtherThanTheFourSecretBearingOnesIsActuallyRetryable() {
        // A quick regression guard on NotificationType.retryable() itself, since
        // EmailNotificationService's entire failure-handling branch depends on it being correct.
        assertThat(NotificationType.EMAIL_VERIFICATION.retryable()).isFalse();
        assertThat(NotificationType.PASSWORD_RESET_REQUESTED.retryable()).isFalse();
        assertThat(NotificationType.LOGIN_OTP.retryable()).isFalse();
        assertThat(NotificationType.STEP_UP_OTP.retryable()).isFalse();
        assertThat(NotificationType.ACCOUNT_CREATED.retryable()).isTrue();
        assertThat(NotificationType.MONEY_MOVEMENT.retryable()).isTrue();
        assertThat(NotificationType.ACCOUNT_CLOSED.retryable()).isTrue();
    }
}

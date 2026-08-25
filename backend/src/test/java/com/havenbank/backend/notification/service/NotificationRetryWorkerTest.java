package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.domain.Status;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The retry worker's per-row outcome, isolated from scheduling and locking: a claimed row that
 * sends successfully clears its stored content and moves to SENT; one that fails again is either
 * rescheduled or dead-lettered by {@code Notification.recordFailure}, and - critically - a failure
 * on one row in a batch must not stop the rest of the batch from being attempted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationRetryWorkerTest {

    @Mock
    private NotificationRepository notifications;
    @Mock
    private EmailSender emailSender;

    private NotificationRetryWorker worker;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        worker = new NotificationRetryWorker(notifications, emailSender);
    }

    @Test
    void aClaimedRowThatSendsSuccessfullyIsMarkedSentAndItsContentIsCleared() {
        Notification pending = pendingRetryable();
        when(notifications.claimDuePending(any(), eq(25))).thenReturn(List.of(pending));

        worker.processDue();

        verify(emailSender).send(eq(pending.getRecipientEmail()), any(), any());
        assertThat(pending.getStatus()).isEqualTo(Status.SENT);
        assertThat(pending.getSubject()).isNull();
        assertThat(pending.getBody()).isNull();
    }

    @Test
    void aClaimedRowThatFailsAgainIsRescheduledRatherThanLeftPending() {
        Notification pending = pendingRetryable();
        doThrow(new RuntimeException("smtp still down")).when(emailSender).send(any(), any(), any());
        when(notifications.claimDuePending(any(), eq(25))).thenReturn(List.of(pending));

        worker.processDue();

        // A freshly-pending notification starts at 0 attempts; one failure brings it to 1, which is
        // under MAX_ATTEMPTS (3) - it must be rescheduled, not dead-lettered, on this single failure.
        assertThat(pending.getStatus()).isEqualTo(Status.PENDING);
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(pending.getLastError()).contains("RuntimeException");
    }

    @Test
    void exhaustingAllAttemptsMovesToDeadLetter() {
        Notification pending = pendingRetryable();
        doThrow(new RuntimeException("permanently broken")).when(emailSender).send(any(), any(), any());
        when(notifications.claimDuePending(any(), eq(25))).thenReturn(List.of(pending));

        // Drive it through every remaining attempt by running the worker repeatedly, exactly as
        // the scheduler would across successive fixed-delay invocations.
        for (int i = 0; i < NotificationRetry.MAX_ATTEMPTS + 1; i++) {
            worker.processDue();
        }

        assertThat(pending.getStatus()).isEqualTo(Status.DEAD_LETTER);
    }

    @Test
    void oneFailingRowInABatchDoesNotPreventTheOthersFromBeingAttempted() {
        Notification willFail = pendingRetryable();
        Notification willSucceed = pendingRetryable();
        when(notifications.claimDuePending(any(), eq(25))).thenReturn(List.of(willFail, willSucceed));
        doThrow(new RuntimeException("down")).when(emailSender)
                .send(eq(willFail.getRecipientEmail()), any(), any());
        // willSucceed's address is different, so its stub is the default (no exception) - only
        // willFail's specific address throws.

        worker.processDue();

        assertThat(willFail.getStatus()).isNotEqualTo(Status.SENT);
        assertThat(willSucceed.getStatus()).isEqualTo(Status.SENT);
    }

    @Test
    void noDueNotificationsMeansNoEmailAttemptsAtAll() {
        when(notifications.claimDuePending(any(), eq(25))).thenReturn(List.of());

        worker.processDue();

        verifyNoInteractions(emailSender);
    }

    private Notification pendingRetryable() {
        NotificationMessage message = new NotificationMessage(
                "customer-" + UUID.randomUUID() + "@example.com", "Customer",
                UUID.randomUUID(), NotificationType.ACCOUNT_CREATED, Map.of());
        return Notification.pending(message, PreferenceGate.EMAIL, "Welcome", "Hi there");
    }
}

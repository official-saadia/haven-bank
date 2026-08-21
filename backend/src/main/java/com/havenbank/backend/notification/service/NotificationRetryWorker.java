package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Redelivers notifications that failed their immediate send. Claims a batch of due rows with
 * {@code FOR UPDATE SKIP LOCKED} (safe across horizontally scaled instances), retries each, and lets
 * the aggregate decide whether to reschedule or dead-letter. Managed entities flush on commit.
 *
 * <p>Note: the delivery I/O runs inside the claiming transaction, so a small batch is used to bound
 * how long row locks are held. At higher volume, claim-then-send in separate transactions (or
 * ShedLock) would be the next step.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationRetryWorker {

    private static final int BATCH = 25;

    private final NotificationRepository notifications;
    private final EmailSender emailSender;

    @Scheduled(fixedDelayString = "${app.notification.retry.interval-ms:60000}")
    @Transactional
    public void processDue() {
        List<Notification> due = notifications.claimDuePending(Instant.now(), BATCH);
        for (Notification n : due) {
            try {
                emailSender.send(n.getRecipientEmail(), n.getSubject(), n.getBody());
                n.markSent();
            } catch (Exception ex) {
                log.warn("Notification retry failed id={} attempt={}", n.getId(), n.getAttempts() + 1, ex);
                n.recordFailure(NotificationRetry.errorText(ex), NotificationRetry.MAX_ATTEMPTS,
                        NotificationRetry.nextAttempt(n.getAttempts() + 1));
            }
        }
    }
}

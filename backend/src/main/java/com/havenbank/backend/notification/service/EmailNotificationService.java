package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.domain.Status;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email-channel {@link NotificationService}. Renders a minimal template per notification type and
 * delegates to an {@link EmailSender}. Runs {@link Async asynchronously}: a delivery failure is
 * logged and, for non-secret types, enqueued for retry by NotificationRetryWorker; never propagates back to the originating operation
 * (FR-7.4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class EmailNotificationService implements NotificationService {

    private final EmailSender emailSender;
    private final PreferenceGate preferenceGate;
    private final NotificationRepository notifications;

    @Override
    @Async
    public void send(NotificationMessage message) {
        if (!preferenceGate.isAllowed(message.recipientUserId(), message.type(), PreferenceGate.EMAIL)) {
            notifications.save(Notification.of(message, PreferenceGate.EMAIL, Status.SUPPRESSED));
            log.debug("Notification suppressed by preference: type={}", message.type());
            return;
        }
        String subject = subjectFor(message.type());
        String body = render(message);
        try {
            emailSender.send(message.recipientEmail(), subject, body);
            notifications.save(Notification.of(message, PreferenceGate.EMAIL, Status.SENT));
        } catch (Exception ex) {
            // Never break the business flow. Retryable (non-secret) notifications go to the outbox for
            // the retry worker; secret-bearing ones are recorded FAILED and not retried (FR-5.3), and
            // the user re-requests (their content is time-sensitive anyway).
            log.warn("Notification dispatch failed for type={}", message.type(), ex);
            if (message.type().retryable()) {
                Notification n = Notification.pending(message, PreferenceGate.EMAIL, subject, body);
                n.recordFailure(NotificationRetry.errorText(ex), NotificationRetry.MAX_ATTEMPTS,
                        NotificationRetry.nextAttempt(1));
                notifications.save(n);
            } else {
                notifications.save(Notification.of(message, PreferenceGate.EMAIL, Status.FAILED));
            }
        }
    }

    private String subjectFor(com.havenbank.backend.notification.domain.NotificationType type) {
        return switch (type) {
            case ACCOUNT_CREATED -> "Welcome to Haven Bank - your account is ready";
            case EMAIL_VERIFICATION -> "Verify your email address";
            case REGISTRATION_ATTEMPT_EXISTING -> "You already have a Haven Bank account";
            case PASSWORD_CHANGED -> "Your password was changed";
            case PASSWORD_RESET_REQUESTED -> "Reset your password";
            case LOGIN_OTP -> "Your one-time login code";
            case STEP_UP_OTP -> "Confirm your transfer";
            case MONEY_MOVEMENT -> "A transaction on your account";
            case ACCOUNT_CLOSED -> "Your Haven Bank account has been closed";
        };
    }

    private String render(NotificationMessage m) {
        String name = m.recipientName() == null ? "there" : m.recipientName();
        return switch (m.type()) {
            case ACCOUNT_CREATED -> "Hi " + name + "\n, your Haven Bank account is now active.";
            case EMAIL_VERIFICATION -> "Hi " + name + "\n, please verify your email: "
                    + m.parameters().getOrDefault("verificationUrl", "");
            // Sent instead of a verification link when the address is already registered. Both
            // paths send mail, so the response cannot be used to discover who banks here (FR-1.7).
            case REGISTRATION_ATTEMPT_EXISTING -> "Hi " + name + "\n, someone just tried to open a "
                    + "Haven Bank account with this email address. You already have one, so no new "
                    + "account was created and nothing has changed. If it was you, sign in at "
                    + m.parameters().getOrDefault("signInUrl", "")
                    + ". If it wasn't, you can safely ignore this - but consider changing your "
                    + "password if you reuse it elsewhere.";
            case PASSWORD_CHANGED -> "Hi " + name + "\n, your password was just changed. "
                    + "If this wasn't you, contact support immediately.";
            case PASSWORD_RESET_REQUESTED -> "Hi " + name + "\n, reset your password: "
                    + m.parameters().getOrDefault("resetUrl", "");
            case LOGIN_OTP -> "Hi " + name + "\n, your one-time login code is "
                    + m.parameters().getOrDefault("code", "") + ". It expires in 5 minutes.";
            case STEP_UP_OTP -> "Hi " + name + "\n, your code to confirm a high-value transfer is "
                    + m.parameters().getOrDefault("code", "") + ".";
            case MONEY_MOVEMENT ->
                    "Hi " + name + "\n, " + m.parameters().getOrDefault("summary", "a transaction occurred on your account") + ".";
            case ACCOUNT_CLOSED -> "Hi " + name + "\n, your Haven Bank account has been closed and can "
                    + "no longer be used to sign in. This is permanent. If you did not expect this, "
                    + "contact support immediately.";
        };
    }
}
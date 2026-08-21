package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.dto.ChangePasswordRequest;
import com.havenbank.backend.iam.dto.ForgotPasswordRequest;
import com.havenbank.backend.iam.dto.ResetPasswordRequest;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.InvalidTokenException;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Handles authenticated password change and the forgotten-password reset flow. Every password change
 * emits a {@code PASSWORD_CHANGED} security-critical notification (non-suppressible, FR-7.1b) and an
 * audit record. Reset is enumeration-safe (FR-1.7): {@link #forgotPassword} always returns normally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService tokenService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.verification.base-url}")
    private String baseUrl;

    /**
     * Change the password of the authenticated user after verifying their current one.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.record(AuditEvent.failure(userId, AuditAction.PASSWORD_CHANGE, "wrong current password"));
            throw new BusinessException(ErrorType.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                    "Current password is incorrect");
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        notifyChanged(user);
        auditService.record(AuditEvent.success(userId, AuditAction.PASSWORD_CHANGE, "password changed"));
    }

    /**
     * Begin a reset. Always returns normally to avoid revealing whether the email exists.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String token = tokenService.issue(OneTimeTokenType.PASSWORD_RESET, user.getId());
            notificationService.send(new NotificationMessage(
                    user.getEmail(), user.getFullName(), NotificationType.PASSWORD_RESET_REQUESTED,
                    Map.of("resetUrl", baseUrl + "/reset-password?token=" + token)));
        });
        auditService.record(AuditEvent.success(null, AuditAction.PASSWORD_RESET_REQUESTED, "reset requested"));
    }

    /**
     * Complete a reset with a valid single-use token.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = tokenService.consume(OneTimeTokenType.PASSWORD_RESET, request.token())
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid or expired"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid or expired"));

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        notifyChanged(user);
        auditService.record(AuditEvent.success(userId, AuditAction.PASSWORD_RESET, "password reset"));
    }

    private void notifyChanged(User user) {
        notificationService.send(new NotificationMessage(
                user.getEmail(), user.getFullName(), NotificationType.PASSWORD_CHANGED, Map.of()));
    }
}

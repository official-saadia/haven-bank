package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.dto.RegisterRequest;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import com.havenbank.backend.shared.error.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates registration and email verification.
 *
 * <p><strong>Anti-enumeration policy (FR-1.7):</strong> {@link #register} never reveals whether an
 * email is already taken. If the address is free, a user is created and a verification email sent; if
 * it is taken, the method returns normally without creating anything. Callers always receive the same
 * {@code 202}. The account-creation confirmation email is sent at <em>verification</em> time, i.e.
 * when the account actually becomes usable.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService tokenService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.verification.base-url}")
    private String baseUrl;

    /**
     * Register a prospective customer. Idempotent from the caller's perspective (always succeeds).
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            // Email the existing owner instead of doing nothing. Because mail goes out on both
            // paths, the API response and the UI copy are identical either way and cannot be used
            // to discover which addresses hold accounts (FR-1.7).
            notificationService.send(new NotificationMessage(
                    request.email(), request.fullName(), NotificationType.REGISTRATION_ATTEMPT_EXISTING,
                    Map.of("signInUrl", baseUrl)));
            auditService.record(AuditEvent.failure(null, AuditAction.REGISTER, "email already registered"));
            log.info("Registration attempt for existing email suppressed; owner notified");
            return;
        }

        Role customerRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role missing: " + DEFAULT_ROLE));

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .build();
        user.addRole(customerRole);
        userRepository.save(user);

        String token = tokenService.issue(OneTimeTokenType.EMAIL_VERIFICATION, user.getId());
        notificationService.send(new NotificationMessage(
                user.getEmail(), user.getFullName(), NotificationType.EMAIL_VERIFICATION,
                Map.of("verificationUrl", baseUrl + "/verify-email?token=" + token)));

        auditService.record(AuditEvent.success(user.getId(), AuditAction.REGISTER, "registered"));
        log.info("Registered user {}", user.getId());
    }

    /**
     * Verify an email token, activate the account, and send the account-created confirmation.
     */
    @Transactional
    public void verifyEmail(String token) {
        UUID userId = tokenService.consume(OneTimeTokenType.EMAIL_VERIFICATION, token)
                .orElseThrow(() -> new InvalidTokenException("Verification token is invalid or expired"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Verification token is invalid or expired"));

        user.markEmailVerified();

        notificationService.send(new NotificationMessage(
                user.getEmail(), user.getFullName(), NotificationType.ACCOUNT_CREATED, Map.of()));
        auditService.record(AuditEvent.success(user.getId(), AuditAction.EMAIL_VERIFIED, "email verified"));
        log.info("Verified email for user {}", user.getId());
    }
}

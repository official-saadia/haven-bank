package com.havenbank.backend.iam.service;

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
import com.havenbank.backend.shared.error.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Password change and reset: verified, notified, and enumeration-safe (FR-1.7, FR-1.11).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OneTimeTokenService tokenService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private PasswordService passwordService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordService, "baseUrl", "https://havenbank.test");
        user = User.builder().email("alice@example.com").passwordHash("{bcrypt}old").fullName("Alice").build();
        userId = user.getId();
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}new");
    }

    @Test
    void changingRequiresTheCurrentPasswordToMatch() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "{bcrypt}old")).thenReturn(false);

        assertThatThrownBy(() -> passwordService.changePassword(userId,
                new ChangePasswordRequest("wrong", "a-brand-new-passphrase")))
                .isInstanceOf(BusinessException.class);

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}old");
        verify(notificationService, never()).send(any());
    }

    @Test
    void changingStoresTheNewHashAndNotifiesTheOwner() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-passphrase", "{bcrypt}old")).thenReturn(true);

        passwordService.changePassword(userId, new ChangePasswordRequest("old-passphrase", "a-brand-new-passphrase"));

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.PASSWORD_CHANGED);
    }

    @Test
    void forgotPasswordEmailsAResetLinkWhenTheAccountExists() {
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(OneTimeTokenType.PASSWORD_RESET, userId)).thenReturn("reset-token");

        passwordService.forgotPassword(new ForgotPasswordRequest("alice@example.com"));

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.PASSWORD_RESET_REQUESTED);
        assertThat(sent.getValue().parameters().get("resetUrl")).contains("reset-token");
    }

    @Test
    void forgotPasswordIsSilentForAnUnknownEmailSoAccountsCannotBeEnumerated() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        // Returns normally, exactly as for a known address.
        passwordService.forgotPassword(new ForgotPasswordRequest("nobody@example.com"));

        verify(notificationService, never()).send(any());
        verify(tokenService, never()).issue(any(), any());
    }

    @Test
    void resetConsumesTheTokenAndReplacesTheHash() {
        when(tokenService.consume(OneTimeTokenType.PASSWORD_RESET, "reset-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        passwordService.resetPassword(new ResetPasswordRequest("reset-token", "a-brand-new-passphrase"));

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
        verify(tokenService).consume(OneTimeTokenType.PASSWORD_RESET, "reset-token");
    }

    @Test
    void resetRejectsAnInvalidOrReplayedToken() {
        when(tokenService.consume(OneTimeTokenType.PASSWORD_RESET, "used")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequest("used", "a-brand-new-passphrase")))
                .isInstanceOf(InvalidTokenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}old");
    }
}

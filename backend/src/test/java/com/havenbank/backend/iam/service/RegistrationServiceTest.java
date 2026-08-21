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
 * Registration must never leak whether an email is already taken (FR-1.7).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    private static final RegisterRequest REQUEST =
            new RegisterRequest("alice@example.com", "correct-horse-battery", "Alice Example");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OneTimeTokenService tokenService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(registrationService, "baseUrl", "https://havenbank.test");
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(org.mockito.Mockito.mock(Role.class)));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}$2a$10$hash");
        when(tokenService.issue(any(), any())).thenReturn("verification-token");
    }

    @Test
    void storesOnlyAHashNeverThePlaintextPassword() {
        when(userRepository.existsByEmailIgnoreCase(REQUEST.email())).thenReturn(false);

        registrationService.register(REQUEST);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash())
                .isEqualTo("{bcrypt}$2a$10$hash")
                .isNotEqualTo(REQUEST.password());
        verify(passwordEncoder).encode(REQUEST.password());
    }

    @Test
    void newAccountStartsUnverifiedAndInactive() {
        when(userRepository.existsByEmailIgnoreCase(REQUEST.email())).thenReturn(false);

        registrationService.register(REQUEST);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        assertThat(saved.getValue().isActive()).isFalse();
    }

    @Test
    void sendsAVerificationEmailContainingTheToken() {
        when(userRepository.existsByEmailIgnoreCase(REQUEST.email())).thenReturn(false);

        registrationService.register(REQUEST);

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.EMAIL_VERIFICATION);
        assertThat(sent.getValue().parameters().get("verificationUrl"))
                .contains("verification-token");
    }

    @Test
    void silentlyDoesNothingWhenTheEmailIsAlreadyRegistered() {
        when(userRepository.existsByEmailIgnoreCase(REQUEST.email())).thenReturn(true);

        // No exception: the caller cannot tell this apart from a successful registration.
        registrationService.register(REQUEST);

        verify(userRepository, never()).save(any());
        verify(notificationService, never()).send(any());
    }

    @Test
    void auditsTheSuppressedDuplicateRegistrationAsAFailure() {
        when(userRepository.existsByEmailIgnoreCase(REQUEST.email())).thenReturn(true);

        registrationService.register(REQUEST);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.REGISTER);
        assertThat(event.getValue().outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
    }

    @Test
    void failsFastWhenTheDefaultRoleIsMissing() {
        when(userRepository.existsByEmailIgnoreCase(REQUEST.email())).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.register(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void verificationActivatesTheAccountAndConfirmsByEmail() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().email("alice@example.com").passwordHash("h").fullName("Alice").build();
        when(tokenService.consume(OneTimeTokenType.EMAIL_VERIFICATION, "tok")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        registrationService.verifyEmail("tok");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isActive()).isTrue();

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.ACCOUNT_CREATED);
    }

    @Test
    void rejectsAnUnknownOrAlreadyUsedVerificationToken() {
        when(tokenService.consume(OneTimeTokenType.EMAIL_VERIFICATION, "bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmail("bad"))
                .isInstanceOf(InvalidTokenException.class);
        verify(notificationService, never()).send(any());
    }
}

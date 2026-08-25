package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.domain.UserStatus;
import com.havenbank.backend.iam.mapper.UserMapper;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.notification.service.NotificationService;
import com.havenbank.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Segregation-of-duties (an admin cannot edit their own roles; CUSTOMER cannot be combined with
 * STAFF/ADMIN) and account lifecycle transitions - none of this had unit coverage before.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private RoleRepository roles;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserAdminService service;

    @Test
    void anAdminCannotChangeTheirOwnRoles() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> service.setRoles(adminId, adminId, List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(roles);
    }

    @Test
    void customerCannotBeCombinedWithAdminOrStaff() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User targetUser = activeUser();
        when(users.findById(target)).thenReturn(Optional.of(targetUser));
        Role customerRole = Role.builder().name("CUSTOMER").build();
        Role adminRole = Role.builder().name("ADMIN").build();
        when(roles.findAllById(any())).thenReturn(List.of(customerRole, adminRole));

        assertThatThrownBy(() -> service.setRoles(actor, target, List.of(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aUserMustEndUpWithAtLeastOneRole() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(activeUser()));
        when(roles.findAllById(any())).thenReturn(List.of()); // none of the supplied ids resolved

        assertThatThrownBy(() -> service.setRoles(actor, target, List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rolesCannotBeChangedOnAClosedAccount() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User closed = activeUser();
        closed.deactivate();
        when(users.findById(target)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.setRoles(actor, target, List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void settingAValidNonConflictingRoleSetSucceeds() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(activeUser()));
        when(roles.findAllById(any())).thenReturn(List.of(Role.builder().name("STAFF").build()));

        service.setRoles(actor, target, List.of(UUID.randomUUID()));

        verify(auditService).record(any());
    }

    @Test
    void lockingAnAccountTransitionsToLockedAndAudits() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User user = activeUser();
        when(users.findById(target)).thenReturn(Optional.of(user));

        service.lock(actor, target);

        assertThat(user.isActive()).isFalse();
        verify(auditService).record(any());
    }

    @Test
    void unlockingRestoresActiveWhenEmailIsAlreadyVerified() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User user = activeUser();
        user.lock();
        when(users.findById(target)).thenReturn(Optional.of(user));

        service.unlock(actor, target);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void deactivatingSendsANonSuppressibleNotificationAndAudits() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User user = activeUser();
        when(users.findById(target)).thenReturn(Optional.of(user));

        service.deactivate(actor, target);

        assertThat(user.isActive()).isFalse();
        verify(auditService).record(any());
        verify(notificationService).send(any());
    }

    @Test
    void gettingANonexistentUserIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(users.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(missing))
                .isInstanceOf(com.havenbank.backend.shared.error.ResourceNotFoundException.class);
    }

    /**
     * A minimal active {@link User} built without going through registration, since only lifecycle
     * state matters here, not the full registration invariants.
     */
    private User activeUser() {
        User user = User.builder().email("test+" + UUID.randomUUID() + "@example.com")
                .passwordHash("irrelevant-for-this-test").fullName("Test User").build();
        user.markEmailVerified();
        return user;
    }
}

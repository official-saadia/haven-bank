package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.dto.RoleRequest;
import com.havenbank.backend.iam.mapper.RoleMapper;
import com.havenbank.backend.iam.repository.PermissionRepository;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * System roles (CUSTOMER/STAFF/ADMIN) are structural - the app's authorization logic depends on
 * them existing with fixed names, so they cannot be deleted or re-permissioned through the admin
 * API regardless of who is asking. Never unit-tested before.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleAdminServiceTest {

    @Mock
    private RoleRepository roles;
    @Mock
    private PermissionRepository permissions;
    @Mock
    private UserRepository users;
    @Mock
    private AuditService auditService;
    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private RoleAdminService service;

    @Test
    void aSystemRoleCannotBeDeletedEvenIfNoUserHoldsItAnymore() {
        UUID actor = UUID.randomUUID();
        Role customerRole = Role.builder().name("CUSTOMER").build();
        when(roles.findById(customerRole.getId())).thenReturn(Optional.of(customerRole));

        assertThatThrownBy(() -> service.delete(actor, customerRole.getId()))
                .isInstanceOf(BusinessException.class);
        verify(roles, never()).delete(any());
    }

    @Test
    void aNonSystemRoleStillAssignedToAUserCannotBeDeleted() {
        UUID actor = UUID.randomUUID();
        Role customRole = Role.builder().name("LOAN_OFFICER").build();
        when(roles.findById(customRole.getId())).thenReturn(Optional.of(customRole));
        when(users.countUsersWithRole(customRole.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(actor, customRole.getId()))
                .isInstanceOf(BusinessException.class);
        verify(roles, never()).delete(any());
    }

    @Test
    void anUnassignedNonSystemRoleCanBeDeleted() {
        UUID actor = UUID.randomUUID();
        Role customRole = Role.builder().name("LOAN_OFFICER").build();
        when(roles.findById(customRole.getId())).thenReturn(Optional.of(customRole));
        when(users.countUsersWithRole(customRole.getId())).thenReturn(0L);

        service.delete(actor, customRole.getId());

        verify(roles).delete(customRole);
        verify(auditService).record(any());
    }

    @Test
    void aSystemRolesPermissionsCannotBeChanged() {
        UUID actor = UUID.randomUUID();
        Role staffRole = Role.builder().name("STAFF").build();
        when(roles.findById(staffRole.getId())).thenReturn(Optional.of(staffRole));

        assertThatThrownBy(() -> service.setPermissions(actor, staffRole.getId(), List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aNonSystemRolesPermissionsCanBeReplaced() {
        UUID actor = UUID.randomUUID();
        Role customRole = Role.builder().name("LOAN_OFFICER").build();
        when(roles.findById(customRole.getId())).thenReturn(Optional.of(customRole));
        when(permissions.findAllById(any())).thenReturn(List.of());

        service.setPermissions(actor, customRole.getId(), List.of(UUID.randomUUID()));

        verify(auditService).record(any());
    }

    @Test
    void creatingARoleWithAnAlreadyTakenNameIsRejected() {
        UUID actor = UUID.randomUUID();
        when(roles.existsByName("CUSTOMER")).thenReturn(true);

        assertThatThrownBy(() -> service.create(actor, new RoleRequest("CUSTOMER", "desc")))
                .isInstanceOf(BusinessException.class);
        verify(roles, never()).save(any());
    }

    @Test
    void gettingANonexistentRoleIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(roles.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), missing, new RoleRequest("X", "Y")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

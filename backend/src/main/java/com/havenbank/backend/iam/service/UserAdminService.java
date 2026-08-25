package com.havenbank.backend.iam.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.domain.UserStatus;
import com.havenbank.backend.iam.dto.AdminUserResponse;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import com.havenbank.backend.iam.mapper.UserMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(userMapper::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        return userMapper.toAdminResponse(user(userId));
    }

    @Transactional
    public AdminUserResponse setRoles(UUID actor, UUID userId, List<UUID> roleIds) {
        if (userId.equals(actor)) {
            throw new BusinessException(ErrorType.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "An admin cannot change their own roles");
        }
        User user = user(userId);
        if (user.getStatus() == UserStatus.CLOSED) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "Roles cannot be changed on a closed account");
        }
        Set<Role> newRoles = new HashSet<>(roles.findAllById(roleIds));
        if (newRoles.isEmpty()) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "A user must have at least one role");
        }
        Set<String> names = newRoles.stream().map(Role::getName).collect(Collectors.toSet());
        // Segregation of duties: a money-holding CUSTOMER must never also hold a privileged role.
        if (names.contains("CUSTOMER") && (names.contains("ADMIN") || names.contains("STAFF"))) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "CUSTOMER cannot be combined with STAFF or ADMIN");
        }
        user.replaceRoles(newRoles);
        auditService.record(new AuditEvent(actor, AuditAction.USER_ROLES_UPDATED, "User",
                userId.toString(), AuditEvent.Outcome.SUCCESS, "roles updated"));
        return userMapper.toAdminResponse(user);
    }

    @Transactional
    public void lock(UUID actor, UUID userId) {
        user(userId).lock();
        auditService.record(new AuditEvent(actor, AuditAction.USER_LOCKED, "User", userId.toString(),
                AuditEvent.Outcome.SUCCESS, "locked"));
    }

    @Transactional
    public void unlock(UUID actor, UUID userId) {
        user(userId).unlock();
        auditService.record(new AuditEvent(actor, AuditAction.USER_UNLOCKED, "User", userId.toString(),
                AuditEvent.Outcome.SUCCESS, "unlocked"));
    }

    @Transactional
    public void deactivate(UUID actor, UUID userId) {
        User user = user(userId);
        user.deactivate();
        auditService.record(new AuditEvent(actor, AuditAction.USER_DEACTIVATED, "User", userId.toString(),
                AuditEvent.Outcome.SUCCESS, "deactivated"));
        // Security-critical, non-suppressible notice (FR-7.1b). Async: a mail failure must not roll
        // back the closure (FR-7.4), which has already been recorded above.
        notificationService.send(new NotificationMessage(
                user.getEmail(), user.getFullName(), user.getId(), NotificationType.ACCOUNT_CLOSED, java.util.Map.of()));
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

}
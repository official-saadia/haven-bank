package com.havenbank.backend.authserver.login;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Feeds Spring Security authentication events into the lockout counter and the audit trail. Password
 * success resets the counter; a bad-credentials failure increments it and may trigger a lock.
 */
@Component
@RequiredArgsConstructor
class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        loginAttemptService.reset(email);
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        String email = event.getAuthentication().getName();
        boolean nowLocked = loginAttemptService.recordFailure(email);
        // There is no authenticated principal on a failed login, so actorUserId stays null; but the
        // attempted identity is known and is exactly what makes the trail useful for spotting an
        // attack. Record it structurally as the target (never the password - FR-5.3). This is the
        // internal, staff-only audit trail, not the client response, so it does not weaken the
        // enumeration protection of FR-1.7.
        String attempted = email != null ? email : "unknown";
        auditService.record(new AuditEvent(null, AuditAction.LOGIN_FAILURE,
                "User", attempted, AuditEvent.Outcome.FAILURE, "bad credentials"));
        if (nowLocked) {
            auditService.record(new AuditEvent(null, AuditAction.ACCOUNT_LOCKED,
                    "User", attempted, AuditEvent.Outcome.FAILURE, "progressive lock"));
        }
    }
}

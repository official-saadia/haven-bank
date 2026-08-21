package com.havenbank.backend.authserver.controller;

import com.havenbank.backend.authserver.otp.OtpService;
import com.havenbank.backend.iam.service.UserDirectory;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Invoked after a successful username/password authentication. Rather than completing login, it
 * issues an email OTP, records the pending user in the session, <strong>clears the security
 * context</strong> (so the user is not yet fully authenticated), and redirects to the OTP page.
 * Full authentication is granted only once {@link OtpController} verifies the code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String PENDING_EMAIL = "MFA_PENDING_EMAIL";

    /**
     * The Authentication produced by the password step, parked until OTP completes.
     *
     * <p>Held rather than rebuilt because the framework attaches metadata that a hand-made token
     * does not have - notably the authentication time, which {@code JwtGenerator} requires to emit
     * the OIDC {@code auth_time} claim. Reconstructing the token loses it and token generation then
     * fails with "authenticationTime cannot be null".
     */
    public static final String PENDING_AUTH = "MFA_PENDING_AUTHENTICATION";

    /**
     * The chain's own repository (see WebSecurityConfig): save and load must be symmetric.
     */
    private final SecurityContextRepository securityContextRepository;

    private final OtpService otpService;
    private final NotificationService notificationService;
    private final UserDirectory userDirectory;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();

        String code = otpService.issue(email);
        String name = userDirectory.findByEmail(email).map(u -> u.fullName()).orElse(null);
        notificationService.send(new NotificationMessage(
                email, name, NotificationType.LOGIN_OTP, Map.of("code", code)));

        request.getSession().setAttribute(PENDING_EMAIL, email);
        request.getSession().setAttribute(PENDING_AUTH, authentication);

        // Undo the primary authentication: the user is only partially authenticated until OTP.
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);

        log.info("Password verified for {}; awaiting OTP", email);
        response.sendRedirect(request.getContextPath() + "/login/otp");
    }
}
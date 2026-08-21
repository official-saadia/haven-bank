package com.havenbank.backend.authserver.controller;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.authserver.otp.OtpService;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

/**
 * Handles the second authentication factor. Renders the OTP page and verifies the submitted code;
 * on success it constructs the fully-authenticated {@link Authentication}, persists it, and resumes
 * the originally requested {@code /oauth2/authorize} request that was cached when login began.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class OtpController {

    /**
     * Where to land when there is no OAuth request to resume - i.e. the user came straight to
     * /login rather than being redirected here by the SPA. The backend serves no home page.
     */
    @Value("${app.spa.base-url:http://localhost:5173}")
    private String spaBaseUrl;

    /**
     * The chain's own repository (see WebSecurityConfig): save and load must be symmetric.
     */
    private final SecurityContextRepository securityContextRepository;

    private final OtpService otpService;
    private final UserDetailsService userDetailsService;
    private final AuditService auditService;

    /**
     * The shared, filtered cache (see WebSecurityConfig) - must be the same one the chains write to.
     */
    private final RequestCache requestCache;

    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/login/otp")
    public String otpPage(HttpSession session) {
        Object pending = session.getAttribute(MfaAuthenticationSuccessHandler.PENDING_EMAIL);
        log.info("OTP page: session={} pending={}", session.getId(),
                pending == null ? "ABSENT -> back to /login" : pending);
        return pending == null ? "redirect:/login" : "otp";
    }

    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping("/login/otp")
    public String verify(@RequestParam("code") String code,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession();
        Object pending = session.getAttribute(MfaAuthenticationSuccessHandler.PENDING_EMAIL);
        log.info("OTP submit: session={} pending={} codeLength={}",
                session.getId(), pending, code == null ? 0 : code.length());

        if (pending == null) {
            // Either the challenge was already consumed (a second submit, or a refresh) or the
            // session was replaced. Nothing to verify against.
            log.warn("OTP submit rejected: no pending email on session {} -> /login", session.getId());
            return "redirect:/login";
        }
        String email = pending.toString();

        boolean verified = otpService.verify(email, code);
        log.info("OTP check for {}: {}", email, verified ? "ACCEPTED" : "REJECTED");
        if (!verified) {
            auditService.record(AuditEvent.failure(null, AuditAction.LOGIN_FAILURE, "otp mismatch"));
            return "redirect:/login/otp?error";
        }

        // Restore the Authentication the password step produced rather than building a new one:
        // it carries the authentication time and any factor metadata the framework attached, both
        // of which JwtGenerator needs when it issues the ID token.
        Object parked = session.getAttribute(MfaAuthenticationSuccessHandler.PENDING_AUTH);
        Authentication authentication;
        if (parked instanceof Authentication parkedAuth) {
            authentication = parkedAuth;
        } else {
            log.warn("No parked authentication for {}; rebuilding from user details", email);
            UserDetails user = userDetailsService.loadUserByUsername(email);
            authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, user.getAuthorities());
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        session.removeAttribute(MfaAuthenticationSuccessHandler.PENDING_EMAIL);
        session.removeAttribute(MfaAuthenticationSuccessHandler.PENDING_AUTH);
        auditService.record(AuditEvent.success(null, AuditAction.LOGIN_SUCCESS, email));

        SavedRequest saved = requestCache.getRequest(request, response);
        // Consume it. Left in the session, a stale authorization request is resumed on the *next*
        // sign-in too, carrying the old `state`; the SPA then rejects the callback as unrecognised
        // ("No matching state found in storage") and never attempts the code exchange at all.
        requestCache.removeRequest(request, response);
        String target = saved != null ? saved.getRedirectUrl() : spaBaseUrl;
        log.info("OTP verified for {}; session={} savedRequest={} -> {}",
                email, session.getId(), saved != null, target);
        return "redirect:" + target;
    }
}
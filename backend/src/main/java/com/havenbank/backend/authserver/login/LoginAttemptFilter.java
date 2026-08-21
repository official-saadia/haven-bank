package com.havenbank.backend.authserver.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Short-circuits a login POST when the account is currently locked, redirecting back to the login
 * page with a {@code locked} flag before credentials are even checked.
 */
@RequiredArgsConstructor
public class LoginAttemptFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath())) {
            String username = request.getParameter("username");
            if (loginAttemptService.isLocked(username)) {
                response.sendRedirect(request.getContextPath() + "/login?locked");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}

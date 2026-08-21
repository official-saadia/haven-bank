package com.havenbank.backend.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets defensive security headers on every response (NFR-1.6): a strict Content-Security-Policy,
 * nosniff, frame denial, a tight referrer policy, and HSTS. The CSP is relaxed only for the Swagger
 * UI paths, which require inline scripts/styles to function.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * The SPA origin must appear in {@code form-action}. Browsers apply that directive to the whole
     * redirect chain that follows a form submission, not just the initial POST - so the login and
     * OTP forms, which end by redirecting to the client's callback, are blocked without it. The
     * symptom is silent: the browser simply stays on the form.
     */
    @Value("${app.spa.base-url:http://localhost:5173}")
    private String spaBaseUrl;

    private static final String STRICT_CSP_TEMPLATE =
            "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; "
                    + "font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self' %s; "
                    + "frame-ancestors 'none'";

    /**
     * The server-rendered auth pages load the same webfonts as the SPA. Under the strict policy the
     * stylesheet ({@code style-src 'self'}) and the font files ({@code font-src 'self'}) are both
     * blocked, and the failure is silent - the page simply renders in fallback fonts, which is why
     * it stopped matching the rest of the product. The two Google origins are therefore allowed
     * here and nowhere else; the API and every other response keep the strict policy.
     *
     * <p>Self-hosting the fonts under {@code /static/fonts} would remove this exception entirely and
     * is the better end state: a login page that makes no third-party request leaks no referrer or
     * client IP to a third party at the moment a customer is about to type a password.
     */
    private static final String AUTH_CSP_TEMPLATE =
            "default-src 'self'; style-src 'self' https://fonts.googleapis.com; script-src 'self'; "
                    + "img-src 'self' data:; font-src 'self' https://fonts.gstatic.com; "
                    + "object-src 'none'; base-uri 'self'; form-action 'self' %s; "
                    + "frame-ancestors 'none'";

    private static final String DOCS_CSP =
            "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self' data:";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean docs = uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
        boolean authPage = uri.equals("/login") || uri.startsWith("/login/");

        response.setHeader("Content-Security-Policy",
                docs ? DOCS_CSP
                        : (authPage ? AUTH_CSP_TEMPLATE : STRICT_CSP_TEMPLATE).formatted(spaBaseUrl));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        filterChain.doFilter(request, response);
    }
}
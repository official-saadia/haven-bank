package com.havenbank.backend.iam.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for first-run administrator bootstrap.
 *
 * <p>The system ships with an ADMIN <em>role</em> but no ADMIN <em>user</em> — and admin endpoints
 * require the role, so there is a genuine chicken-and-egg on a fresh database: you cannot grant admin
 * without an admin. This bootstrap resolves it the way production systems do — a first-run seed from
 * externalised configuration, never a hardcoded credential in a migration.
 *
 * <p>It is <strong>off unless {@code enabled} is true and both an email and a password are supplied</strong>,
 * and it is a no-op if any ADMIN already exists — so it creates exactly one admin, once, and does
 * nothing on every subsequent boot. The password is injected (env/secret), not committed.
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String fullName
) {
    public boolean isConfigured() {
        return enabled
                && email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }

    public String resolvedName() {
        return (fullName == null || fullName.isBlank()) ? "Administrator" : fullName;
    }
}
package com.havenbank.backend.iam.bootstrap;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.repository.RoleRepository;
import com.havenbank.backend.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator on startup, once, from externalised configuration.
 *
 * <p>Guarantees, in order of the checks below:
 * <ul>
 *   <li><strong>Opt-in.</strong> Does nothing unless {@code app.bootstrap.admin.enabled} is true and
 *       both an email and password are supplied. A misconfigured or default deployment seeds no
 *       privileged account.</li>
 *   <li><strong>Idempotent.</strong> Does nothing if any user already holds the ADMIN role, so it
 *       never creates a second admin and re-running it (every boot) is safe.</li>
 *   <li><strong>No enumeration side-channel.</strong> Runs out of band at startup, not through the
 *       registration path, so it cannot be used to probe which emails exist.</li>
 * </ul>
 *
 * <p>The seeded account is created already-verified and ACTIVE (it has no inbox to confirm from) and
 * is built through the same aggregate behaviour and {@link PasswordEncoder} as a real registration —
 * the password is hashed, never stored or logged in the clear.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAdminIfNeeded() {
        if (!properties.isConfigured()) {
            return;
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE).orElse(null);
        if (adminRole == null) {
            // The role is seeded by V1; its absence means a broken schema, not a reason to invent it.
            log.warn("Admin bootstrap skipped: role {} not found. Check Flyway migrations.", ADMIN_ROLE);
            return;
        }

        if (userRepository.existsByRoleName(ADMIN_ROLE)) {
            log.debug("Admin bootstrap skipped: an administrator already exists.");
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(properties.email())) {
            // The address is taken by a non-admin. Promoting an arbitrary existing account from a
            // config flag would be surprising and unsafe; require a clean address instead.
            log.warn("Admin bootstrap skipped: {} already exists as a non-admin user. "
                    + "Grant ADMIN manually or use a different bootstrap email.", properties.email());
            return;
        }

        User admin = User.builder()
                .email(properties.email())
                .passwordHash(passwordEncoder.encode(properties.password()))
                .fullName(properties.resolvedName())
                .build();
        admin.markEmailVerified(); // active immediately - there is no inbox to verify from
        admin.addRole(adminRole);
        userRepository.save(admin);

        // Log the identity, never the secret.
        log.info("Bootstrapped initial administrator: {}", admin.getEmail());
    }
}
package com.havenbank.backend.iam.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Supplies a {@link PasswordEncoder}. A {@code DelegatingPasswordEncoder} stores the algorithm id as
 * a {@code {bcrypt}} prefix, so the work factor can be raised - or the algorithm migrated to
 * Argon2id - later, transparently re-hashing on next successful login without a forced reset
 * (FR-1.3, implementation note).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

package com.havenbank.backend.shared.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Holds the AES key used for column encryption, derived (SHA-256) from the configured secret so any
 * passphrase length yields a valid 256-bit key. The key is exposed statically so it is reachable from
 * the JPA {@link CryptoConverter}, which the persistence provider instantiates outside the Spring
 * container. In production the secret must be injected from a secrets manager, never defaulted.
 */
@Slf4j
@Component
public class CryptoKeyHolder {

    private static final String DEV_DEFAULT = "change-me-dev-only-secret";
    private static volatile SecretKey key;

    public CryptoKeyHolder(@Value("${app.crypto.key:change-me-dev-only-secret}") String secret) {
        if (DEV_DEFAULT.equals(secret)) {
            log.warn("Using the DEFAULT development encryption key. Set app.crypto.key in production.");
        }
        try {
            byte[] material = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(material, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive encryption key", ex);
        }
    }

    static SecretKey key() {
        SecretKey k = key;
        if (k == null) {
            throw new IllegalStateException("Encryption key not initialised");
        }
        return k;
    }
}

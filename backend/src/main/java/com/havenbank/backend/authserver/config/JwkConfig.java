package com.havenbank.backend.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Provides the RSA signing key, exposed to resource servers via JWKS.
 *
 * <p>If {@code app.security.keystore.path} is configured, the key pair is loaded from a persistent
 * PKCS12 keystore (so tokens survive restarts and {@code kid}-based rotation works across instances).
 * Otherwise an <strong>ephemeral</strong> key is generated - convenient for local dev, unsuitable for
 * production. Create a keystore with, e.g.:
 * {@code keytool -genkeypair -alias banking -keyalg RSA -keysize 2048 -keystore keys.p12
 * -storetype PKCS12}.</p>
 */
@Slf4j
@Configuration
public class JwkConfig {

    @Value("${app.security.keystore.path:}")
    private String keystorePath;

    @Value("${app.security.keystore.password:}")
    private String keystorePassword;

    @Value("${app.security.keystore.alias:banking}")
    private String keystoreAlias;

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = StringUtils.hasText(keystorePath) ? loadFromKeystore() : generateEphemeral();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private RSAKey loadFromKeystore() {
        try {
            Resource resource = new DefaultResourceLoader().getResource(keystorePath);
            char[] password = keystorePassword.toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = resource.getInputStream()) {
                keyStore.load(in, password);
            }
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keystoreAlias, password);
            Certificate certificate = keyStore.getCertificate(keystoreAlias);
            RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
            log.info("Loaded RSA signing key from keystore (alias={})", keystoreAlias);
            return new RSAKey.Builder(publicKey)
                    .privateKey((RSAPrivateKey) privateKey)
                    .keyID(keystoreAlias)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load signing key from keystore " + keystorePath, ex);
        }
    }

    private RSAKey generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            log.warn("Using an EPHEMERAL RSA signing key. Configure app.security.keystore.* for production.");
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate RSA key", ex);
        }
    }
}

package com.havenbank.backend.shared.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Column encryption must round-trip and must not be deterministic (NFR-1.5).
 */
class CryptoConverterTest {

    private final CryptoConverter converter = new CryptoConverter();

    @BeforeAll
    static void initialiseKey() {
        // The holder publishes the derived key statically for the JPA converter.
        new CryptoKeyHolder("unit-test-encryption-secret");
    }

    @Test
    void roundTripsAValue() {
        String plaintext = "47 Ledger Lane, London";
        String stored = converter.convertToDatabaseColumn(plaintext);

        assertThat(stored).isNotNull().isNotEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(plaintext);
    }

    @Test
    void producesDifferentCiphertextEachTimeBecauseTheIvIsRandom() {
        String plaintext = "same input";

        String first = converter.convertToDatabaseColumn(plaintext);
        String second = converter.convertToDatabaseColumn(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext);
    }

    @Test
    void passesNullThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTripsAnEmptyString() {
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(""))).isEmpty();
    }

    @Test
    void rejectsTamperedCiphertextBecauseGcmIsAuthenticated() {
        String stored = converter.convertToDatabaseColumn("sensitive");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}

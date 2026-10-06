package com.stacc.backend.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Base64;

import com.stacc.backend.TestSecrets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwtPropertiesTest {

    @Test
    void strongSecretAndShortLifetimeAreAccepted() {
        JwtProperties properties = new JwtProperties(TestSecrets.JWT_SECRET, 15);

        assertEquals(Duration.ofMinutes(15), properties.accessTokenLifetime());
        assertEquals("HmacSHA256", properties.signingKey().getAlgorithm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "${JWT_SECRET}"})
    void secretThatIsNotSetIsReportedClearly(String secret) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new JwtProperties(secret, 15));

        assertEquals("JWT_SECRET is not set", exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(null, 15));
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret", "stacc", "password", "123456", "mysecretkey", "not base64 at all!"})
    void weakOrMalformedSecretIsRejected(String secret) {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(secret, 15));
    }

    @Test
    void secretMustHoldAtLeast256Bits() {
        String thirtyOneBytes = Base64.getEncoder().encodeToString(new byte[31]);
        String thirtyTwoBytes = Base64.getEncoder().encodeToString(new byte[32]);

        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(thirtyOneBytes, 15));
        assertEquals(32, new JwtProperties(thirtyTwoBytes, 15).signingKey().getEncoded().length);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 61, 10080})
    void lifetimeMustStayShort(int minutes) {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(TestSecrets.JWT_SECRET, minutes));
    }

    @Test
    void problemsAndTextFormNeverRevealTheSecret() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new JwtProperties(shortSecret, 15));

        assertFalse(exception.getMessage().contains(shortSecret));
        assertFalse(new JwtProperties(TestSecrets.JWT_SECRET, 15).toString().contains(TestSecrets.JWT_SECRET));
    }
}

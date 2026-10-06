package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

    // Made-up values used only by these tests.
    private static final String PASSWORD = "TestPassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword123!";

    private final PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

    @Test
    void encodedPasswordIsNotThePlainPassword() {
        String hash = passwordEncoder.encode(PASSWORD);

        assertNotEquals(PASSWORD, hash);
        assertFalse(hash.contains(PASSWORD));
    }

    @Test
    void correctPasswordMatchesItsHash() {
        String hash = passwordEncoder.encode(PASSWORD);

        assertTrue(passwordEncoder.matches(PASSWORD, hash));
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = passwordEncoder.encode(PASSWORD);

        assertFalse(passwordEncoder.matches(WRONG_PASSWORD, hash));
    }

    @Test
    void samePasswordGetsADifferentHashEachTime() {
        String first = passwordEncoder.encode(PASSWORD);
        String second = passwordEncoder.encode(PASSWORD);

        assertNotEquals(first, second);
        assertTrue(passwordEncoder.matches(PASSWORD, first));
        assertTrue(passwordEncoder.matches(PASSWORD, second));
    }

    @Test
    void hashNamesItsAlgorithmAndFitsAUserAccount() {
        String hash = passwordEncoder.encode(PASSWORD);

        assertTrue(hash.startsWith("{bcrypt}"));
        // The password_hash column holds 255 characters.
        assertTrue(hash.length() <= 255);
        UserAccount account = new UserAccount("EMP1024", hash);
        assertTrue(passwordEncoder.matches(PASSWORD, account.getPasswordHash()));
    }
}

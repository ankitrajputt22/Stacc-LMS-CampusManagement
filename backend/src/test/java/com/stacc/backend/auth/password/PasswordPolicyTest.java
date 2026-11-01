package com.stacc.backend.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"river lamp 42 cocoa", "Tq7#vB2!pX9z", "correct horse battery staple", "दस अक्षरों वाला पासवर्ड"})
    void longUncommonPasswordsAreAccepted(String password) {
        assertThat(PasswordPolicy.problems(password, "2408400100011")).isEmpty();
    }

    @Test
    void tooShortIsRefusedAndCountsCharactersNotBytes() {
        assertThat(PasswordPolicy.problems("short1", "S1")).singleElement().asString().contains("at least 10");
        assertThat(PasswordPolicy.problems("ñandú río1", "S1")).isEmpty();
    }

    @Test
    void tooLongIsRefused() {
        assertThat(PasswordPolicy.problems("a1b2c3d4e5".repeat(13), "S1")).anyMatch(p -> p.contains("at most 128"));
    }

    @Test
    void containingTheLoginIdIsRefusedWhateverTheCase() {
        assertThat(PasswordPolicy.problems("my emp1024 password!", "EMP1024"))
                .anyMatch(p -> p.contains("login ID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123", "Password 123", "QWERTY123", "student@123", "aaaaaaaaaaaa", "abababababab"})
    void commonOrRepetitivePasswordsAreRefused(String password) {
        assertThat(PasswordPolicy.problems(password, "S1")).anyMatch(p -> p.contains("too easy to guess"));
    }

    @Test
    void blankIsRefused() {
        assertThat(PasswordPolicy.problems("   ", "S1")).containsExactly("Choose a new password.");
        assertThat(PasswordPolicy.problems(null, "S1")).containsExactly("Choose a new password.");
    }
}

package com.stacc.backend.auth.password;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What makes a new password acceptable. Length matters most, so there are no rules about symbols or capitals; instead
 * a password must be long enough, must not contain the login ID, and must not be one of the passwords people pick most
 * often. Spaces are allowed and kept: a passphrase is a good password. The password itself is never logged or returned.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    // Short on purpose: the worst offenders, including college-flavoured ones. Compared without case or spaces.
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd",
            "qwerty", "qwerty123", "qwertyuiop", "asdfghjkl", "1234567890", "0987654321", "1111111111", "0000000000",
            "123456789", "12345678910", "abcdefghij", "iloveyou", "welcome123", "welcome@123", "letmein123",
            "admin12345", "administrator", "changeme123", "student123", "student@123", "faculty123", "college123",
            "college@123", "stacc12345", "stacc@123", "india12345", "india@123", "sunshine123", "princess123",
            "football123", "cricket123", "monkey12345", "dragon12345", "master12345", "trustno1", "zxcvbnm123");

    private PasswordPolicy() {
    }

    /** Everything wrong with the new password, in plain words; empty when it is acceptable. */
    public static List<String> problems(String password, String loginId) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isBlank()) {
            problems.add("Choose a new password.");
            return problems;
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            problems.add("Use at least " + MIN_LENGTH + " characters. A short phrase is easy to remember.");
        }
        if (length > MAX_LENGTH) {
            problems.add("Use at most " + MAX_LENGTH + " characters.");
        }
        String plain = password.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (loginId != null && !loginId.isBlank() && plain.contains(loginId.strip().toLowerCase(Locale.ROOT))) {
            problems.add("Do not include your login ID.");
        }
        if (COMMON.contains(plain) || plain.chars().distinct().count() <= 2) {
            problems.add("This password is too easy to guess. Choose something less common.");
        }
        return problems;
    }
}

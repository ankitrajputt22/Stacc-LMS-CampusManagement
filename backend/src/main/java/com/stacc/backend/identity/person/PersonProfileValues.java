package com.stacc.backend.identity.person;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The rules for the values on a person profile, in one place, so the entity, the requests, and the demo data agree.
 * Names are plain text of ordinary characters; blank optional values become null.
 */
public final class PersonProfileValues {

    public static final int MAX_FULL_NAME = 150;
    public static final int MAX_PREFERRED_NAME = 80;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_PHONE = 20;

    // No control characters in a name: they could break a page, a CSV export, or a log line.
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    // A deliberately plain check: something@somewhere.tld with no spaces. The college's own records are the authority.
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    // Digits with an optional leading +, and spaces, dashes, or brackets between them: 7 to 15 digits in all.
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9 ()-]+");

    private PersonProfileValues() {
    }

    public static String fullName(String value) {
        String name = name(value, MAX_FULL_NAME, "Full name");
        if (name == null) {
            throw new IllegalArgumentException("Full name is required.");
        }
        return name;
    }

    public static String preferredName(String value) {
        return name(value, MAX_PREFERRED_NAME, "Preferred name");
    }

    public static String email(String value) {
        String email = blankToNull(value);
        if (email == null) {
            return null;
        }
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Email address is not valid.");
        }
        return email.toLowerCase(Locale.ROOT);
    }

    public static String phone(String value) {
        String phone = blankToNull(value);
        if (phone == null) {
            return null;
        }
        long digits = phone.chars().filter(Character::isDigit).count();
        if (phone.length() > MAX_PHONE || !PHONE.matcher(phone).matches() || digits < 7 || digits > 15) {
            throw new IllegalArgumentException("Phone number must have 7 to 15 digits, with an optional leading +.");
        }
        return phone;
    }

    private static String name(String value, int max, String label) {
        String name = blankToNull(value);
        if (name == null) {
            return null;
        }
        name = name.replaceAll("\\s+", " ");
        if (name.length() > max) {
            throw new IllegalArgumentException(label + " must be at most " + max + " characters.");
        }
        if (CONTROL.matcher(name).find()) {
            throw new IllegalArgumentException(label + " contains characters that are not allowed.");
        }
        return name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

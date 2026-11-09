package com.stacc.backend.auth.permission;

import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An action that can later be granted to roles. Permissions are identified by
 * a text code and are added as real features need them.
 */
@Entity
@Table(name = "permissions")
public class Permission {

    public static final int MAX_CODE_LENGTH = 100;

    // UPPERCASE_WITH_UNDERSCORES, for example COURSE_VIEW.
    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");

    // Names starting with ROLE_ mean a role to Spring Security, so a permission may not use them.
    private static final String ROLE_PREFIX = "ROLE_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    protected Permission() {
        // Required by JPA.
    }

    /**
     * Surrounding spaces are removed. Any other code that is not already in
     * uppercase with underscores is rejected rather than changed.
     */
    public Permission(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        String trimmed = code.strip();
        if (trimmed.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("code must be at most " + MAX_CODE_LENGTH + " characters");
        }
        if (!CODE_FORMAT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("code must use uppercase letters, digits, and underscores");
        }
        if (trimmed.startsWith(ROLE_PREFIX)) {
            throw new IllegalArgumentException("code must not start with " + ROLE_PREFIX);
        }
        this.code = trimmed;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    // Two permissions are the same permission when they have the same unique code.
    @Override
    public boolean equals(Object other) {
        return other instanceof Permission permission && Objects.equals(code, permission.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}

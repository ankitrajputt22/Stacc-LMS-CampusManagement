package com.stacc.backend.auth.permission;

import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission {

    public static final int MAX_CODE_LENGTH = 100;

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");

    private static final String ROLE_PREFIX = "ROLE_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    protected Permission() {
    }

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

    @Override
    public boolean equals(Object other) {
        return other instanceof Permission permission && Objects.equals(code, permission.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}

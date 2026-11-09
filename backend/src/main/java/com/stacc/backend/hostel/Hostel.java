package com.stacc.backend.hostel;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A hostel building. Its code is set once. An inactive hostel takes no new allocations; its history stays. Hostels are
 * never deleted.
 */
@Entity
@Table(name = "hostels")
public class Hostel {

    public static final int MAX_CODE_LENGTH = 50;
    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Hostel() {
        // Required by JPA.
    }

    public Hostel(String code, String name, String description) {
        this.code = normalizedCode(code);
        update(name, description, true);
    }

    /** The code as it is stored: trimmed and uppercase, letters, digits, hyphens, and underscores. */
    public static String normalizedCode(String code) {
        String normalized = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be letters, digits, hyphens, and underscores");
        }
        return normalized;
    }

    public void update(String name, String description, boolean active) {
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
        this.description = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

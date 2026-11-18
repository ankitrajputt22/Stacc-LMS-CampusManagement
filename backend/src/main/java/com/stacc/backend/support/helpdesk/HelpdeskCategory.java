package com.stacc.backend.support.helpdesk;

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

@Entity
@Table(name = "helpdesk_categories")
public class HelpdeskCategory {

    public static final int MAX_CODE_LENGTH = 50;
    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

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

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "default_team_id")
    private HelpdeskTeam defaultTeam;

    public HelpdeskTeam getDefaultTeam() {
        return defaultTeam;
    }

    public void routeTo(HelpdeskTeam team) {
        this.defaultTeam = team;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HelpdeskCategory() {
    }

    public HelpdeskCategory(String code, String name, String description) {
        this.code = normalizedCode(code);
        update(name, description, true);
    }

    public static String normalizedCode(String code) {
        String normalized = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be letters, digits, and underscores, starting with a letter");
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

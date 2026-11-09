package com.stacc.backend.engagement.groups;

import java.time.Instant;
import java.util.Locale;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A club or sports group. Its code is fixed; it is made inactive, never deleted. */
@Entity
@Table(name = "campus_groups")
public class CampusGroup {

    public enum Kind { CLUB, SPORT }

    /** How people join: at once, after an officer accepts, or not at all. */
    public enum JoinPolicy { OPEN, APPROVAL, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 20)
    private JoinPolicy joinPolicy;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampusGroup() {
        // Required by JPA.
    }

    public CampusGroup(String code, String name, Kind kind, String description, JoinPolicy joinPolicy) {
        String normalized = Require.text(code, "code", 50).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("the code uses capital letters, digits, and underscores, starting with a letter");
        }
        this.code = normalized;
        update(name, kind, description, joinPolicy, true);
    }

    public void update(String name, Kind kind, String description, JoinPolicy joinPolicy, boolean active) {
        this.name = Require.text(name, "name", 150);
        this.kind = Require.notNull(kind, "kind");
        this.description = Require.optionalText(description, "description", 2000);
        this.joinPolicy = Require.notNull(joinPolicy, "join policy");
        this.active = active;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Kind getKind() { return kind; }
    public String getDescription() { return description; }
    public JoinPolicy getJoinPolicy() { return joinPolicy; }
    public boolean isActive() { return active; }
}

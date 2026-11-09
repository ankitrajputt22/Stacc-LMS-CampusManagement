package com.stacc.backend.support.helpdesk;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A support team that categories route tickets to. Its code is fixed; a team is made inactive, never deleted. */
@Entity
@Table(name = "helpdesk_teams")
public class HelpdeskTeam {

    public static final int MAX_NAME_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = HelpdeskCategory.MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HelpdeskTeam() {
        // Required by JPA.
    }

    public HelpdeskTeam(String code, String name) {
        this.code = HelpdeskCategory.normalizedCode(code);
        update(name, true);
    }

    public void update(String name, boolean active) {
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
        this.active = active;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}

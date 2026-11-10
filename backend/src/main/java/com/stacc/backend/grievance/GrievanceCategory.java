package com.stacc.backend.grievance;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A kind of grievance with its committee's response time. The code is fixed; categories are made inactive, never deleted. */
@Entity
@Table(name = "grievance_categories")
public class GrievanceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "response_days", nullable = false)
    private int responseDays;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GrievanceCategory() {
    }

    public GrievanceCategory(String code, String name, int responseDays, Instant createdAt) {
        String upper = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!upper.matches("[A-Z][A-Z0-9_]{1,49}")) {
            throw new IllegalArgumentException("Use capital letters, digits, and underscores for the code.");
        }
        this.code = upper;
        this.createdAt = createdAt;
        change(name, responseDays, true);
    }

    public void change(String newName, int days, boolean nowActive) {
        String stripped = newName == null ? "" : newName.strip();
        if (stripped.isEmpty() || stripped.length() > 150) {
            throw new IllegalArgumentException("Enter the category's name.");
        }
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("The response time is 1 to 90 days.");
        }
        this.name = stripped;
        this.responseDays = days;
        this.active = nowActive;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getResponseDays() { return responseDays; }
    public boolean isActive() { return active; }
}

package com.stacc.backend.career;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A company that recruits through the college. Made inactive, never deleted. */
@Entity
@Table(name = "career_companies")
public class CareerCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String website;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CareerCompany() {
        // Required by JPA.
    }

    public CareerCompany(String name, String website, String description, Instant now) {
        this.createdAt = now;
        update(name, website, description, true);
    }

    public void update(String name, String website, String description, boolean active) {
        String site = Require.optionalText(website, "website", 300);
        if (site != null && !site.startsWith("https://")) {
            throw new IllegalArgumentException("the website must start with https://");
        }
        this.name = Require.text(name, "name", 200);
        this.website = site;
        this.description = Require.optionalText(description, "description", 2000);
        this.active = active;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getWebsite() { return website; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}

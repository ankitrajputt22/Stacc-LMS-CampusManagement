package com.stacc.backend.career;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student's career profile: a headline, skills, and links, one per account, written by the student. */
@Entity
@Table(name = "career_profiles")
public class CareerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(length = 200)
    private String headline;

    @Column(length = 1000)
    private String skills;

    @Column(length = 1500)
    private String links;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerProfile() {
        // Required by JPA.
    }

    public CareerProfile(Long accountId, Instant now) {
        this.accountId = accountId;
        this.updatedAt = now;
    }

    /** Skills and links are newline-separated lists; links must be https. */
    public void edit(String headline, String skills, String links, Instant now) {
        String cleanLinks = Require.optionalText(links, "links", 1500);
        if (cleanLinks != null) {
            for (String link : cleanLinks.split("\n")) {
                if (!link.isBlank() && !link.strip().matches("^https://[^\\s]{3,300}$")) {
                    throw new IllegalArgumentException("each link must start with https://");
                }
            }
        }
        this.headline = Require.optionalText(headline, "headline", 200);
        this.skills = Require.optionalText(skills, "skills", 1000);
        this.links = cleanLinks;
        this.updatedAt = now;
    }

    public Long getAccountId() { return accountId; }
    public String getHeadline() { return headline; }
    public String getSkills() { return skills; }
    public String getLinks() { return links; }
}

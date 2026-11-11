package com.stacc.backend.projects;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A student project led by its creator. Open projects take join requests; projects are closed or archived, never deleted. */
@Entity
@Table(name = "projects")
public class Project {

    public enum Status { OPEN, CLOSED, ARCHIVED }

    public static final int MAX_SUMMARY = 5_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_account_id", nullable = false, updatable = false)
    private UserAccount lead;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(UserAccount lead, String title, String summary, int maxMembers, Instant createdAt) {
        this.lead = lead;
        this.createdAt = createdAt;
        edit(title, summary, maxMembers);
    }

    public void edit(String newTitle, String newSummary, int newMax) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This project is archived.");
        }
        String stripped = newTitle == null ? "" : newTitle.strip();
        if (stripped.isEmpty() || stripped.length() > 200) {
            throw new IllegalArgumentException("Enter the project's title.");
        }
        String text = newSummary == null ? "" : newSummary.strip();
        if (text.isEmpty() || text.length() > MAX_SUMMARY) {
            throw new IllegalArgumentException("Describe the project in up to 5,000 characters.");
        }
        if (newMax < 1 || newMax > 20) {
            throw new IllegalArgumentException("A project has 1 to 20 members, the lead included.");
        }
        this.title = stripped;
        this.summary = text;
        this.maxMembers = newMax;
    }

    /** Opens or closes the project to join requests; an archived project stays archived. */
    public void setStatus(Status next) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This project is archived.");
        }
        status = next;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public UserAccount getLead() { return lead; }
    public int getMaxMembers() { return maxMembers; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.stacc.backend.career;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
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
import org.hibernate.annotations.UpdateTimestamp;

/** A student's application, with the resume version it was sent with, consent to share, and its stage. */
@Entity
@Table(name = "career_applications")
public class CareerApplication {

    public enum Status { APPLIED, IN_PROGRESS, SELECTED, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false, updatable = false)
    private CareerOpportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", updatable = false)
    private CareerResume resume;

    @Column(name = "cover_note", length = 2000, updatable = false)
    private String coverNote;

    @Column(name = "share_consent", nullable = false, updatable = false)
    private boolean shareConsent;

    @Column(name = "stage_index", nullable = false)
    private int stageIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.APPLIED;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerApplication() {
        // Required by JPA.
    }

    public CareerApplication(CareerOpportunity opportunity, UserAccount applicant, CareerResume resume, String coverNote,
            boolean shareConsent, Instant now) {
        this.opportunity = opportunity;
        this.applicant = applicant;
        this.resume = resume;
        this.coverNote = Require.optionalText(coverNote, "cover note", 2000);
        this.shareConsent = shareConsent;
        this.appliedAt = now;
    }

    public boolean isActive() {
        return status == Status.APPLIED || status == Status.IN_PROGRESS;
    }

    /** Moves to the next stage; the last stage makes it SELECTED. */
    public void advance(int stageCount) {
        if (!isActive()) {
            throw new IllegalStateException("This application is finished");
        }
        if (stageIndex + 1 >= stageCount) {
            status = Status.SELECTED;
        } else {
            stageIndex++;
            status = Status.IN_PROGRESS;
        }
    }

    public void reject() {
        if (!isActive()) {
            throw new IllegalStateException("This application is finished");
        }
        status = Status.REJECTED;
    }

    public void withdraw() {
        if (!isActive()) {
            throw new IllegalStateException("This application is finished");
        }
        status = Status.WITHDRAWN;
    }

    public Long getId() { return id; }
    public CareerOpportunity getOpportunity() { return opportunity; }
    public UserAccount getApplicant() { return applicant; }
    public CareerResume getResume() { return resume; }
    public String getCoverNote() { return coverNote; }
    public boolean isShareConsent() { return shareConsent; }
    public int getStageIndex() { return stageIndex; }
    public Status getStatus() { return status; }
    public Instant getAppliedAt() { return appliedAt; }
}

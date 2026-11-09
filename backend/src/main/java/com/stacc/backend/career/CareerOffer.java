package com.stacc.backend.career;

import java.time.Instant;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** An offer made to a selected applicant, which the student accepts or declines once. */
@Entity
@Table(name = "career_offers")
public class CareerOffer {

    public enum Status { MADE, ACCEPTED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private CareerApplication application;

    @Column(name = "role_title", nullable = false, updatable = false, length = 200)
    private String roleTitle;

    @Column(length = 200, updatable = false)
    private String compensation;

    @Column(name = "respond_by", updatable = false)
    private Instant respondBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.MADE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected CareerOffer() {
        // Required by JPA.
    }

    public CareerOffer(CareerApplication application, String roleTitle, String compensation, Instant respondBy, Instant now) {
        this.application = application;
        this.roleTitle = Require.text(roleTitle, "role", 200);
        this.compensation = Require.optionalText(compensation, "compensation", 200);
        this.respondBy = respondBy;
        this.createdAt = now;
    }

    public void respond(boolean accept, Instant at) {
        if (status != Status.MADE) {
            throw new IllegalStateException("This offer was already answered");
        }
        status = accept ? Status.ACCEPTED : Status.DECLINED;
        respondedAt = at;
    }

    public Long getId() { return id; }
    public CareerApplication getApplication() { return application; }
    public String getRoleTitle() { return roleTitle; }
    public String getCompensation() { return compensation; }
    public Instant getRespondBy() { return respondBy; }
    public Status getStatus() { return status; }
}

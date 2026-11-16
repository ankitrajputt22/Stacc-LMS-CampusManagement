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

/** A student's place on a project: requested, then active or declined; withdrawn or left later. Kept as history. */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    public enum Status { REQUESTED, ACTIVE, DECLINED, WITHDRAWN, LEFT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 500)
    private String message;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected ProjectMember() {
    }

    /** A join request, or the lead's own place when the project is created. */
    public ProjectMember(Project project, UserAccount account, String message, boolean lead, Instant at) {
        String text = message == null || message.isBlank() ? null : message.strip();
        if (text != null && text.length() > 500) {
            throw new IllegalArgumentException("The message is at most 500 characters.");
        }
        this.project = project;
        this.account = account;
        this.message = text;
        this.requestedAt = at;
        this.status = lead ? Status.ACTIVE : Status.REQUESTED;
        this.decidedAt = lead ? at : null;
    }

    void decide(boolean accept, Instant at) {
        if (status != Status.REQUESTED) {
            throw new IllegalStateException("This request was already decided.");
        }
        status = accept ? Status.ACTIVE : Status.DECLINED;
        decidedAt = at;
    }

    void withdrawOrLeave(Instant at) {
        if (status == Status.REQUESTED) {
            status = Status.WITHDRAWN;
        } else if (status == Status.ACTIVE) {
            status = Status.LEFT;
        } else {
            throw new IllegalStateException("You are not on this project.");
        }
        leftAt = at;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public UserAccount getAccount() { return account; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getRequestedAt() { return requestedAt; }
}

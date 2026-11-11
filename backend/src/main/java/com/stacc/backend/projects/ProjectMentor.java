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

/** A faculty member invited to mentor a project: they accept or decline; an accepted mentor may step down. Kept as history. */
@Entity
@Table(name = "project_mentors")
public class ProjectMentor {

    public enum Status { INVITED, ACCEPTED, DECLINED, ENDED }

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
    private Status status = Status.INVITED;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ProjectMentor() {
    }

    public ProjectMentor(Project project, UserAccount account, Instant invitedAt) {
        this.project = project;
        this.account = account;
        this.invitedAt = invitedAt;
    }

    void answer(boolean accept, Instant at) {
        if (status != Status.INVITED) {
            throw new IllegalStateException("This invitation was already answered.");
        }
        status = accept ? Status.ACCEPTED : Status.DECLINED;
        decidedAt = at;
    }

    void end(Instant at) {
        if (status != Status.ACCEPTED) {
            throw new IllegalStateException("Only an accepted mentor can step down.");
        }
        status = Status.ENDED;
        decidedAt = at;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public UserAccount getAccount() { return account; }
    public Status getStatus() { return status; }
}

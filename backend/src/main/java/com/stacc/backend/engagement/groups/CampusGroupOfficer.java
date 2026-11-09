package com.stacc.backend.engagement.groups;

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

/** An officer of a group (faculty advisor, coach, or student officer). Ended, never deleted. */
@Entity
@Table(name = "campus_group_officers")
public class CampusGroupOfficer {

    public enum Title { ADVISOR, COACH, PRESIDENT, SECRETARY, OFFICER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private CampusGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Title title;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CampusGroupOfficer() {
        // Required by JPA.
    }

    public CampusGroupOfficer(CampusGroup group, UserAccount account, Title title, Instant at) {
        this.group = group;
        this.account = account;
        this.title = title;
        this.assignedAt = at;
    }

    public void end(Instant at) {
        if (endedAt == null) {
            endedAt = at;
        }
    }

    public Long getId() { return id; }
    public CampusGroup getGroup() { return group; }
    public UserAccount getAccount() { return account; }
    public Title getTitle() { return title; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isActive() { return endedAt == null; }
}

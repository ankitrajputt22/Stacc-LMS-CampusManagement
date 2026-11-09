package com.stacc.backend.support.helpdesk;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One account's membership of a support team. Membership is configuration; tickets keep their own history. */
@Entity
@Table(name = "helpdesk_team_members")
public class HelpdeskTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private HelpdeskTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HelpdeskTeamMember() {
        // Required by JPA.
    }

    public HelpdeskTeamMember(HelpdeskTeam team, UserAccount account, Instant createdAt) {
        this.team = team;
        this.account = account;
        this.createdAt = createdAt;
    }

    public HelpdeskTeam getTeam() { return team; }
    public UserAccount getAccount() { return account; }
}

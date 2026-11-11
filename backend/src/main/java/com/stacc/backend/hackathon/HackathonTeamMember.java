package com.stacc.backend.hackathon;

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

/** A student's place on a team; at most one live place per hackathon. Leaving keeps the row. */
@Entity
@Table(name = "hackathon_team_members")
public class HackathonTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private HackathonTeam team;

    @Column(name = "hackathon_id", nullable = false, updatable = false)
    private Long hackathonId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected HackathonTeamMember() {
    }

    public HackathonTeamMember(HackathonTeam team, UserAccount account, Instant joinedAt) {
        this.team = team;
        this.hackathonId = team.getHackathon().getId();
        this.account = account;
        this.joinedAt = joinedAt;
    }

    void leave(Instant at) {
        if (leftAt == null) {
            leftAt = at;
        }
    }

    public Long getId() { return id; }
    public HackathonTeam getTeam() { return team; }
    public UserAccount getAccount() { return account; }
    public Instant getLeftAt() { return leftAt; }
}

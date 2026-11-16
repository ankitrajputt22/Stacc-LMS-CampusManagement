package com.stacc.backend.engagement.teams;

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

/** A player on a team's roster, with their role. Leaving keeps the row as history. */
@Entity
@Table(name = "campus_team_members")
public class CampusTeamMember {

    public enum Role { PLAYER, CAPTAIN, VICE_CAPTAIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private CampusTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected CampusTeamMember() {
    }

    public CampusTeamMember(CampusTeam team, UserAccount account, Role role, Instant joinedAt) {
        this.team = team;
        this.account = account;
        this.role = role == null ? Role.PLAYER : role;
        this.joinedAt = joinedAt;
    }

    void changeRole(Role newRole) {
        if (leftAt != null) {
            throw new IllegalStateException("This player has left the team.");
        }
        this.role = newRole == null ? Role.PLAYER : newRole;
    }

    void leave(Instant at) {
        if (leftAt != null) {
            throw new IllegalStateException("This player has already left the team.");
        }
        this.leftAt = at;
    }

    public Long getId() { return id; }
    public CampusTeam getTeam() { return team; }
    public UserAccount getAccount() { return account; }
    public Role getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }
}

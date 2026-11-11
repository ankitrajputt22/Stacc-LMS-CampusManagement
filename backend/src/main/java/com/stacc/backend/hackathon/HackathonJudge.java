package com.stacc.backend.hackathon;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A faculty member named to judge a hackathon. */
@Entity
@Table(name = "hackathon_judges")
public class HackathonJudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hackathon_id", nullable = false, updatable = false)
    private Hackathon hackathon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    protected HackathonJudge() {
    }

    public HackathonJudge(Hackathon hackathon, UserAccount account) {
        this.hackathon = hackathon;
        this.account = account;
    }

    public Long getId() { return id; }
    public Hackathon getHackathon() { return hackathon; }
    public UserAccount getAccount() { return account; }
}

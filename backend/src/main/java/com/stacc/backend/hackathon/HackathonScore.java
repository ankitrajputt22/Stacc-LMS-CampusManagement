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

/** One judge's score (0 to 100) for a submission, replaced until results are released. */
@Entity
@Table(name = "hackathon_scores")
public class HackathonScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private HackathonSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "judge_account_id", nullable = false, updatable = false)
    private UserAccount judge;

    @Column(nullable = false)
    private int score;

    @Column(length = 1000)
    private String comment;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    protected HackathonScore() {
    }

    public HackathonScore(HackathonSubmission submission, UserAccount judge) {
        this.submission = submission;
        this.judge = judge;
    }

    public void set(int newScore, String newComment, Instant at) {
        if (newScore < 0 || newScore > 100) {
            throw new IllegalArgumentException("Score from 0 to 100.");
        }
        String text = newComment == null || newComment.isBlank() ? null : newComment.strip();
        if (text != null && text.length() > 1000) {
            throw new IllegalArgumentException("The comment is at most 1,000 characters.");
        }
        this.score = newScore;
        this.comment = text;
        this.scoredAt = at;
    }

    public Long getId() { return id; }
    public HackathonSubmission getSubmission() { return submission; }
    public UserAccount getJudge() { return judge; }
    public int getScore() { return score; }
    public String getComment() { return comment; }
}

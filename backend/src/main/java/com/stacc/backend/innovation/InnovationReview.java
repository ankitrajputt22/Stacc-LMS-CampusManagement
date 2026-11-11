package com.stacc.backend.innovation;

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

/** One reviewer's score (1 to 5) and comment on an idea, replaced by that reviewer while the idea is open. */
@Entity
@Table(name = "innovation_reviews")
public class InnovationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, updatable = false)
    private InnovationIdea idea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_account_id", nullable = false, updatable = false)
    private UserAccount reviewer;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected InnovationReview() {
    }

    public InnovationReview(InnovationIdea idea, UserAccount reviewer) {
        this.idea = idea;
        this.reviewer = reviewer;
    }

    public void set(int newScore, String newComment, Instant at) {
        if (newScore < 1 || newScore > 5) {
            throw new IllegalArgumentException("Score the idea from 1 to 5.");
        }
        this.score = newScore;
        this.comment = InnovationIdea.text(newComment, "Write a comment for the submitter.", InnovationIdea.MAX_TEXT);
        this.reviewedAt = at;
    }

    public Long getId() { return id; }
    public InnovationIdea getIdea() { return idea; }
    public UserAccount getReviewer() { return reviewer; }
    public int getScore() { return score; }
    public String getComment() { return comment; }
    public Instant getReviewedAt() { return reviewedAt; }
}

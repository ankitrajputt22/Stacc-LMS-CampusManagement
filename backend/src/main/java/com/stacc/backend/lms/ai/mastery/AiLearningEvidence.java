package com.stacc.backend.lms.ai.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_learning_evidence")
public class AiLearningEvidence {

    public enum Kind { PRACTICE_ANSWER, FLASHCARD_REVIEW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Kind kind;

    @Column(nullable = false, precision = 4, scale = 3, updatable = false)
    private BigDecimal score;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiLearningEvidence() {
    }

    public AiLearningEvidence(Long lmsCourseId, Long userAccountId, Long topicId, Kind kind, BigDecimal score, Instant createdAt) {
        this.lmsCourseId = lmsCourseId;
        this.userAccountId = userAccountId;
        this.topicId = topicId;
        this.kind = kind;
        this.score = score.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(3, RoundingMode.HALF_UP);
        this.createdAt = createdAt;
    }

    public Long getTopicId() {
        return topicId;
    }

    public Kind getKind() {
        return kind;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

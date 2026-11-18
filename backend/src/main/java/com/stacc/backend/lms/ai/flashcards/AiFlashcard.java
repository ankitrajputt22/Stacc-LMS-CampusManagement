package com.stacc.backend.lms.ai.flashcards;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_flashcards")
public class AiFlashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_flashcard_set_id", nullable = false, updatable = false)
    private AiFlashcardSet set;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 300, updatable = false)
    private String front;

    @Column(nullable = false, length = 1000, updatable = false)
    private String back;

    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private long sourceId;

    @Column(name = "source_title", nullable = false, length = 200, updatable = false)
    private String sourceTitle;

    @Column(name = "page_id", updatable = false)
    private Long pageId;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ease;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private int repetitions;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    protected AiFlashcard() {
    }

    AiFlashcard(AiFlashcardSet set, int position, String front, String back, String sourceType, long sourceId, String sourceTitle,
            Long pageId, LocalDate today) {
        this.set = set;
        this.position = position;
        this.front = Require.text(front, "front", 300);
        this.back = Require.text(back, "back", 1000);
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceTitle = sourceTitle.length() > 200 ? sourceTitle.substring(0, 200) : sourceTitle;
        this.pageId = pageId;
        this.ease = SpacedRepetition.START_EASE;
        this.dueOn = today;
    }

    public void review(ReviewRating rating, LocalDate today, Instant at) {
        SpacedRepetition.State next = SpacedRepetition.next(new SpacedRepetition.State(ease, intervalDays, repetitions, lapses), rating);
        this.ease = next.ease();
        this.intervalDays = next.intervalDays();
        this.repetitions = next.repetitions();
        this.lapses = next.lapses();
        this.dueOn = today.plusDays(next.intervalDays());
        this.lastReviewedAt = at;
    }

    public boolean isDue(LocalDate today) {
        return !dueOn.isAfter(today);
    }

    public Long getId() {
        return id;
    }

    public AiFlashcardSet getSet() {
        return set;
    }

    public int getPosition() {
        return position;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getSourceType() {
        return sourceType;
    }

    public long getSourceId() {
        return sourceId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public Long getPageId() {
        return pageId;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }
}

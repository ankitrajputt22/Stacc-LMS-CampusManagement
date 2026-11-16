package com.stacc.backend.hostel.mess;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.hostel.Hostel;
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

/** A resident's rating (1 to 5) of one meal, with an optional comment; given once and never changed. */
@Entity
@Table(name = "hostel_meal_feedback")
public class MealFeedback {

    public static final int MAX_COMMENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @Column(name = "student_account_id", nullable = false, updatable = false)
    private Long studentAccountId;

    @Column(name = "meal_date", nullable = false, updatable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Meal meal;

    @Column(nullable = false, updatable = false)
    private int rating;

    @Column(updatable = false, length = MAX_COMMENT_LENGTH)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MealFeedback() {
        // Required by JPA.
    }

    public MealFeedback(Hostel hostel, Long studentAccountId, LocalDate mealDate, Meal meal, int rating, String comment, Instant now) {
        this.hostel = Require.notNull(hostel, "hostel");
        this.studentAccountId = Require.notNull(studentAccountId, "studentAccountId");
        this.mealDate = Require.notNull(mealDate, "Date");
        this.meal = Require.notNull(meal, "Meal");
        this.rating = Require.range(rating, "Rating", 1, 5);
        this.comment = Require.optionalText(comment, "Comment", MAX_COMMENT_LENGTH);
        this.createdAt = Require.notNull(now, "now");
    }

    public LocalDate getMealDate() { return mealDate; }
    public Meal getMeal() { return meal; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
}

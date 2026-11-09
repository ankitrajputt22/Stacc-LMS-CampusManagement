package com.stacc.backend.calendar.personal;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person's own calendar entry: a title, optional notes, and a date range (end included). Only its owner sees it. */
@Entity
@Table(name = "personal_calendar_events")
public class PersonalCalendarEvent {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_NOTES_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_NOTES_LENGTH)
    private String notes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonalCalendarEvent() {
        // Required by JPA.
    }

    public PersonalCalendarEvent(Long userAccountId, String title, String notes, LocalDate startDate, LocalDate endDate, Instant now) {
        this.userAccountId = Require.notNull(userAccountId, "userAccountId");
        this.createdAt = Require.notNull(now, "now");
        change(title, notes, startDate, endDate, now);
    }

    public void change(String title, String notes, LocalDate startDate, LocalDate endDate, Instant now) {
        Require.notNull(startDate, "startDate");
        Require.notNull(endDate, "endDate");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("The end date cannot be before the start date.");
        }
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.notes = Require.optionalText(notes, "notes", MAX_NOTES_LENGTH);
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserAccountId() { return userAccountId; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}

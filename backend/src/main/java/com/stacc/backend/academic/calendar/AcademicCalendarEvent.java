package com.stacc.backend.academic.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.common.validation.Require;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "academic_calendar_events")
public class AcademicCalendarEvent {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 5000;
    public static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
    public static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_session_id", nullable = false)
    private AcademicSession academicSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicCalendarEventCategory category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicCalendarEventStatus status = AcademicCalendarEventStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicCalendarEvent() {
    }

    public AcademicCalendarEvent(AcademicSession academicSession, Semester semester, String title, String description,
            AcademicCalendarEventCategory category, LocalDate startDate, LocalDate endDate) {
        setScope(academicSession, semester);
        setDetails(title, description, category, startDate, endDate);
    }

    public static boolean belongsToSession(Semester semester, AcademicSession academicSession) {
        AcademicSession own = semester.getAcademicSession();
        return own == academicSession
                || (own != null && academicSession != null && own.getId() != null && own.getId().equals(academicSession.getId()));
    }

    public Long getId() {
        return id;
    }

    public AcademicSession getAcademicSession() {
        return academicSession;
    }

    public Semester getSemester() {
        return semester;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public AcademicCalendarEventCategory getCategory() {
        return category;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public AcademicCalendarEventStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCancelled() {
        return status == AcademicCalendarEventStatus.CANCELLED;
    }

    public boolean isVisibleToUsers() {
        return status == AcademicCalendarEventStatus.PUBLISHED
                || (status == AcademicCalendarEventStatus.CANCELLED && publishedAt != null);
    }

    public void edit(String title, String description, AcademicCalendarEventCategory category, LocalDate startDate,
            LocalDate endDate) {
        requireNotCancelled();
        setDetails(title, description, category, startDate, endDate);
    }

    public void rescope(AcademicSession academicSession, Semester semester) {
        if (status != AcademicCalendarEventStatus.DRAFT) {
            throw new IllegalStateException("Only a draft event can move to another session or semester");
        }
        setScope(academicSession, semester);
    }

    public void publish(Instant at) {
        requireNotCancelled();
        if (status != AcademicCalendarEventStatus.PUBLISHED) {
            this.publishedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
            this.status = AcademicCalendarEventStatus.PUBLISHED;
        }
    }

    public void cancel(Instant at) {
        if (status != AcademicCalendarEventStatus.CANCELLED) {
            this.cancelledAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
            this.status = AcademicCalendarEventStatus.CANCELLED;
        }
    }

    private void setScope(AcademicSession academicSession, Semester semester) {
        Require.notNull(academicSession, "academicSession");
        if (semester != null && !belongsToSession(semester, academicSession)) {
            throw new IllegalArgumentException("semester must belong to the event's academic session");
        }
        this.academicSession = academicSession;
        this.semester = semester;
    }

    private void setDetails(String title, String description, AcademicCalendarEventCategory category,
            LocalDate startDate, LocalDate endDate) {
        String checkedTitle = Require.text(title, "title", MAX_TITLE_LENGTH);
        String checkedDescription = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
        Require.notNull(category, "category");
        requireSupported(Require.notNull(startDate, "startDate"), "startDate");
        requireSupported(Require.notNull(endDate, "endDate"), "endDate");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        this.title = checkedTitle;
        this.description = checkedDescription;
        this.category = category;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    private static void requireSupported(LocalDate date, String name) {
        if (date.isBefore(EARLIEST_DATE) || date.isAfter(LATEST_DATE)) {
            throw new IllegalArgumentException(name + " must be between " + EARLIEST_DATE + " and " + LATEST_DATE);
        }
    }

    private void requireNotCancelled() {
        if (isCancelled()) {
            throw new IllegalStateException("A cancelled event cannot be changed");
        }
    }
}

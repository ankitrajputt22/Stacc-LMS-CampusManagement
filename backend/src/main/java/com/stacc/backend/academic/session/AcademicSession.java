package com.stacc.backend.academic.session;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "academic_sessions")
public class AcademicSession {

    public static final int MAX_CODE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicSessionStatus status = AcademicSessionStatus.PLANNED;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicSession() {
    }

    public AcademicSession(String code, LocalDate startDate, LocalDate endDate) {
        this.code = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        setDates(startDate, endDate);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public AcademicSessionStatus getStatus() {
        return status;
    }

    public boolean isCurrent() {
        return current;
    }

    public void makeCurrent() {
        if (status == AcademicSessionStatus.CLOSED) {
            throw new IllegalStateException("A closed academic session cannot be the current one");
        }
        this.current = true;
    }

    public void clearCurrent() {
        this.current = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void reschedule(LocalDate startDate, LocalDate endDate) {
        setDates(startDate, endDate);
    }

    public void markActive() {
        if (status == AcademicSessionStatus.CLOSED) {
            throw new IllegalStateException("A closed academic session cannot be made active again");
        }
        this.status = AcademicSessionStatus.ACTIVE;
    }

    public void close() {
        this.status = AcademicSessionStatus.CLOSED;
        this.current = false;
    }

    private void setDates(LocalDate startDate, LocalDate endDate) {
        Require.notNull(startDate, "startDate");
        Require.notNull(endDate, "endDate");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }
}

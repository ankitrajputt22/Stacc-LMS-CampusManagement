package com.stacc.backend.academic.exam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.stacc.backend.academic.offering.CourseOffering;
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
@Table(name = "exam_slots")
public class ExamSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_session_id", nullable = false, updatable = false)
    private ExamSession examSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, updatable = false)
    private CourseOffering courseOffering;

    @Column(name = "exam_date", nullable = false, updatable = false)
    private LocalDate examDate;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private LocalTime endsAt;

    @Column(nullable = false, length = 40, updatable = false)
    private String hall;

    @Column(name = "hall_seats", nullable = false, updatable = false)
    private int hallSeats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected ExamSlot() {
    }

    public ExamSlot(ExamSession examSession, CourseOffering courseOffering, LocalDate examDate, LocalTime startsAt, LocalTime endsAt,
            String hall, int hallSeats, Instant createdAt) {
        this.examSession = examSession;
        this.courseOffering = courseOffering;
        this.examDate = examDate;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.hall = hall;
        this.hallSeats = hallSeats;
        this.createdAt = createdAt;
    }

    public void remove(Instant at) {
        if (removedAt == null) {
            removedAt = at;
        }
    }

    public Long getId() {
        return id;
    }

    public ExamSession getExamSession() {
        return examSession;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public LocalTime getStartsAt() {
        return startsAt;
    }

    public LocalTime getEndsAt() {
        return endsAt;
    }

    public String getHall() {
        return hall;
    }

    public int getHallSeats() {
        return hallSeats;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}

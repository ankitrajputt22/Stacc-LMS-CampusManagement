package com.stacc.backend.academic.timetable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import com.stacc.backend.academic.offering.CourseOffering;
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

/**
 * One weekly class of a course offering: a day, a start and an end time in the college's time zone, a room, and a
 * kind. Removing a slot keeps it with the time it was removed; nothing is deleted.
 */
@Entity
@Table(name = "class_timetable_slots")
public class ClassTimetableSlot {

    public enum Kind { LECTURE, TUTORIAL, LAB }

    public static final int MAX_ROOM = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, updatable = false)
    private CourseOffering courseOffering;

    @Column(name = "day_of_week", nullable = false, updatable = false)
    private int dayOfWeek;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private LocalTime endsAt;

    @Column(nullable = false, length = MAX_ROOM, updatable = false)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected ClassTimetableSlot() {
    }

    public ClassTimetableSlot(CourseOffering courseOffering, DayOfWeek day, LocalTime startsAt, LocalTime endsAt, String room,
            Kind kind, Instant createdAt) {
        this.courseOffering = courseOffering;
        this.dayOfWeek = day.getValue();
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.room = room;
        this.kind = kind;
        this.createdAt = createdAt;
    }

    /** Whether this slot and the given day and times share any minute. */
    public boolean overlaps(DayOfWeek day, LocalTime start, LocalTime end) {
        return dayOfWeek == day.getValue() && startsAt.isBefore(end) && start.isBefore(endsAt);
    }

    public boolean remove(Instant at) {
        if (removedAt != null) {
            return false;
        }
        removedAt = at;
        return true;
    }

    public Long getId() {
        return id;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public DayOfWeek getDay() {
        return DayOfWeek.of(dayOfWeek);
    }

    public LocalTime getStartsAt() {
        return startsAt;
    }

    public LocalTime getEndsAt() {
        return endsAt;
    }

    public String getRoom() {
        return room;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}

package com.stacc.backend.academic.exam;

import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A student's seat in one exam, through their course enrollment. Seats are only ever added. */
@Entity
@Table(name = "exam_seats")
public class ExamSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_slot_id", nullable = false, updatable = false)
    private ExamSlot examSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    @Column(name = "seat_number", nullable = false, updatable = false)
    private int seatNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExamSeat() {
    }

    public ExamSeat(ExamSlot examSlot, CourseEnrollment courseEnrollment, int seatNumber, Instant createdAt) {
        this.examSlot = examSlot;
        this.courseEnrollment = courseEnrollment;
        this.seatNumber = seatNumber;
        this.createdAt = createdAt;
    }

    public ExamSlot getExamSlot() {
        return examSlot;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
}

package com.stacc.backend.academic.offering;

import java.time.Instant;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.semester.Semester;
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
@Table(name = "course_offerings")
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false, updatable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", updatable = false)
    private Section section;

    @Column
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 20)
    private OfferingRegistrationStatus registrationStatus = OfferingRegistrationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseOfferingStatus status = CourseOfferingStatus.PLANNED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseOffering() {
    }

    public CourseOffering(Course course, Semester semester) {
        this(course, semester, null);
    }

    public CourseOffering(Course course, Semester semester, Section section) {
        this.course = Require.notNull(course, "course");
        this.semester = Require.notNull(semester, "semester");
        if (section != null && !section.getSemester().getId().equals(semester.getId())) {
            throw new IllegalArgumentException("section must belong to the offering's semester");
        }
        this.section = section;
    }

    public Section getSection() {
        return section;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void changeCapacity(Integer capacity) {
        if (capacity != null) {
            Require.range(capacity, "capacity", 1, 1000);
        }
        this.capacity = capacity;
    }

    public OfferingRegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    public void changeRegistrationStatus(OfferingRegistrationStatus registrationStatus) {
        this.registrationStatus = Require.notNull(registrationStatus, "registrationStatus");
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public Semester getSemester() {
        return semester;
    }

    public CourseOfferingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markActive() {
        if (status == CourseOfferingStatus.CLOSED) {
            throw new IllegalStateException("A closed course offering cannot be made active again");
        }
        this.status = CourseOfferingStatus.ACTIVE;
    }

    public void close() {
        this.status = CourseOfferingStatus.CLOSED;
    }
}

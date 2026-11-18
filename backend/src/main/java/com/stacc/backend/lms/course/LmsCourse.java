package com.stacc.backend.lms.course;

import java.time.Instant;

import com.stacc.backend.academic.offering.CourseOffering;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "lms_courses")
public class LmsCourse {

    public static final int MAX_WELCOME_LENGTH = 5_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false, unique = true, updatable = false)
    private CourseOffering courseOffering;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsCourseStatus status = LmsCourseStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "home_view", nullable = false, length = 20)
    private LmsCourseHomeView homeView = LmsCourseHomeView.OVERVIEW;

    @Column(name = "welcome_message", columnDefinition = "MEDIUMTEXT")
    private String welcomeMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsCourse() {
    }

    public LmsCourse(CourseOffering courseOffering) {
        this.courseOffering = Require.notNull(courseOffering, "courseOffering");
    }

    public Long getId() {
        return id;
    }

    public CourseOffering getCourseOffering() {
        return courseOffering;
    }

    public LmsCourseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LmsCourseHomeView getHomeView() {
        return homeView;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void changeHome(LmsCourseHomeView homeView, String welcomeMessage) {
        if (status == LmsCourseStatus.ARCHIVED) {
            throw new IllegalStateException("An archived LMS course cannot be changed");
        }
        this.homeView = Require.notNull(homeView, "homeView");
        this.welcomeMessage = Require.optionalText(welcomeMessage, "welcomeMessage", MAX_WELCOME_LENGTH);
    }

    public void publish() {
        if (status == LmsCourseStatus.ARCHIVED) {
            throw new IllegalStateException("An archived LMS course cannot be published again");
        }
        this.status = LmsCourseStatus.PUBLISHED;
    }

    public void archive() {
        this.status = LmsCourseStatus.ARCHIVED;
    }
}

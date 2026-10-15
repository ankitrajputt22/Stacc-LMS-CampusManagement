package com.stacc.backend.lms.content;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
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

/**
 * One module of an LMS course: a titled group of topics, such as "Introduction" or "Arrays".
 * Modules are listed by position, then by ID. A new module is a draft. A module that is no longer
 * wanted is archived, never deleted, so its topics and history are kept.
 */
@Entity
@Table(name = "lms_modules")
public class LmsModule {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    // No cascade: the LMS course is never created or deleted through one of its modules.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    // Plain text. It is never treated as HTML.
    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsModule() {
        // Required by JPA.
    }

    public LmsModule(LmsCourse lmsCourse, String title, String description, int position) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        edit(title, description);
        moveTo(position);
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getPosition() {
        return position;
    }

    public LmsContentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isArchived() {
        return status == LmsContentStatus.ARCHIVED;
    }

    /** Changes the title and the optional description. An archived module can no longer be changed. */
    public void edit(String title, String description) {
        requireNotArchived();
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.description = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
    }

    /** Places the module in its course's order. Positions start at 1. */
    public void moveTo(int position) {
        this.position = Require.range(position, "position", 1, Integer.MAX_VALUE);
    }

    /** Publishing an already published module changes nothing. It does not publish the topics. */
    public void publish() {
        requireNotArchived();
        this.status = LmsContentStatus.PUBLISHED;
    }

    /** Archiving keeps the module and its topics. Archiving it again changes nothing. */
    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived module cannot be changed");
        }
    }
}

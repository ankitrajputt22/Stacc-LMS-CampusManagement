package com.stacc.backend.lms.announcement;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
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

@Entity
@Table(name = "lms_announcements")
public class LmsAnnouncement {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_format", nullable = false, length = 20)
    private LmsTextFormat messageFormat = LmsTextFormat.PLAIN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsAnnouncement() {
    }

    public LmsAnnouncement(LmsCourse lmsCourse, String title, String message) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        edit(title, message);
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

    public String getMessage() {
        return message;
    }

    public LmsContentStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LmsTextFormat getMessageFormat() {
        return messageFormat;
    }

    public void changeMessageFormat(LmsTextFormat format) {
        requireNotArchived();
        this.messageFormat = Require.notNull(format, "format");
    }

    public boolean isArchived() {
        return status == LmsContentStatus.ARCHIVED;
    }

    public void edit(String title, String message) {
        requireNotArchived();
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.message = Require.text(message, "message", MAX_MESSAGE_LENGTH);
    }

    public void publish(Instant at) {
        requireNotArchived();
        if (status != LmsContentStatus.PUBLISHED) {
            Require.notNull(at, "at");
            this.status = LmsContentStatus.PUBLISHED;
            if (publishedAt == null) {
                this.publishedAt = at;
            }
        }
    }

    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived announcement cannot be changed");
        }
    }
}

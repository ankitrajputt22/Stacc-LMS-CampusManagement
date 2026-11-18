package com.stacc.backend.lms.syllabus;

import java.time.Instant;
import java.util.Objects;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "lms_syllabi")
public class LmsSyllabus {

    public static final int MAX_CONTENT_LENGTH = 30_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false, unique = true)
    private LmsCourse lmsCourse;

    @Column(name = "draft_content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String draftContent;

    @Column(name = "draft_updated_at", nullable = false)
    private Instant draftUpdatedAt;

    @Column(name = "published_content", columnDefinition = "MEDIUMTEXT")
    private String publishedContent;

    @Column(name = "first_published_at")
    private Instant firstPublishedAt;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsSyllabus() {
    }

    public LmsSyllabus(LmsCourse lmsCourse, String draftContent, Instant now) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        saveDraft(draftContent, now);
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public String getDraftContent() {
        return draftContent;
    }

    public Instant getDraftUpdatedAt() {
        return draftUpdatedAt;
    }

    public String getPublishedContent() {
        return publishedContent;
    }

    public Instant getFirstPublishedAt() {
        return firstPublishedAt;
    }

    public Instant getLastPublishedAt() {
        return lastPublishedAt;
    }

    public boolean isPublished() {
        return publishedContent != null;
    }

    public boolean hasUnpublishedChanges() {
        return !Objects.equals(draftContent, publishedContent);
    }

    public void saveDraft(String content, Instant now) {
        this.draftContent = Require.text(content, "content", MAX_CONTENT_LENGTH);
        this.draftUpdatedAt = Require.notNull(now, "now");
    }

    public void publish(Instant now) {
        Require.notNull(now, "now");
        if (!hasUnpublishedChanges()) {
            return;
        }
        this.publishedContent = draftContent;
        if (firstPublishedAt == null) {
            this.firstPublishedAt = now;
        }
        this.lastPublishedAt = now;
    }
}

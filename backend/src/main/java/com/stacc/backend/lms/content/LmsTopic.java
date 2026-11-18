package com.stacc.backend.lms.content;

import java.time.Instant;

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
@Table(name = "lms_topics")
public class LmsTopic {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_CONTENT_LENGTH = 20_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_module_id", nullable = false, updatable = false)
    private LmsModule lmsModule;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false, length = 20)
    private LmsTextFormat contentFormat = LmsTextFormat.PLAIN;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsTopic() {
    }

    public LmsTopic(LmsModule lmsModule, String title, String content, int position) {
        this.lmsModule = Require.notNull(lmsModule, "lmsModule");
        edit(title, content);
        moveTo(position);
    }

    public Long getId() {
        return id;
    }

    public LmsModule getLmsModule() {
        return lmsModule;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public LmsTextFormat getContentFormat() {
        return contentFormat;
    }

    public void changeContentFormat(LmsTextFormat format) {
        requireNotArchived();
        this.contentFormat = Require.notNull(format, "format");
    }

    public boolean isRequired() {
        return required;
    }

    public void markRequired(boolean required) {
        requireNotArchived();
        this.required = required;
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

    public void edit(String title, String content) {
        requireNotArchived();
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.content = Require.optionalText(content, "content", MAX_CONTENT_LENGTH);
    }

    public void moveTo(int position) {
        this.position = Require.range(position, "position", 1, Integer.MAX_VALUE);
    }

    public void publish() {
        requireNotArchived();
        this.status = LmsContentStatus.PUBLISHED;
    }

    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived topic cannot be changed");
        }
    }
}

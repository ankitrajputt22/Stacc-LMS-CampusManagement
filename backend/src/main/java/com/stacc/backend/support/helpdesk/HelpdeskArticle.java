package com.stacc.backend.support.helpdesk;

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
@Table(name = "helpdesk_articles")
public class HelpdeskArticle {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 10_000;

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private HelpdeskCategory category;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_by_account_id", nullable = false)
    private Long updatedByAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HelpdeskArticle() {
    }

    public HelpdeskArticle(HelpdeskCategory category, String title, String body, Long byAccountId) {
        edit(category, title, body, byAccountId);
    }

    public void edit(HelpdeskCategory category, String title, String body, Long byAccountId) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This article is archived and can no longer be changed.");
        }
        this.category = Require.notNull(category, "Category");
        this.title = Require.text(title, "Title", MAX_TITLE_LENGTH);
        this.body = Require.text(body, "Text", MAX_BODY_LENGTH);
        this.updatedByAccountId = Require.notNull(byAccountId, "byAccountId");
    }

    public void publish(Instant at, Long byAccountId) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This article is archived and can no longer be published.");
        }
        if (status == Status.DRAFT) {
            this.status = Status.PUBLISHED;
            this.updatedByAccountId = Require.notNull(byAccountId, "byAccountId");
            if (publishedAt == null) {
                this.publishedAt = Require.notNull(at, "at");
            }
        }
    }

    public void archive(Long byAccountId) {
        if (status != Status.ARCHIVED) {
            this.status = Status.ARCHIVED;
            this.updatedByAccountId = Require.notNull(byAccountId, "byAccountId");
        }
    }

    public int matches(java.util.Collection<String> words) {
        String haystack = (title + "\n" + body).toLowerCase(java.util.Locale.ROOT);
        return (int) words.stream().filter(haystack::contains).count();
    }

    public Long getId() { return id; }
    public HelpdeskCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

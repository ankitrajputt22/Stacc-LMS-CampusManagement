package com.stacc.backend.campus.notices;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One campus notice: a plain-text title and message for the roles it is visible to. It starts DRAFT; publishing sets
 * {@code publishedAt} once; archiving is final. Draft and published notices can be corrected, archived ones cannot. It
 * is never deleted, restored, or scheduled.
 */
@Entity
@Table(name = "campus_notices")
public class CampusNotice {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "visible_to_student", nullable = false)
    private boolean visibleToStudent;

    @Column(name = "visible_to_faculty", nullable = false)
    private boolean visibleToFaculty;

    @Column(name = "visible_to_admin", nullable = false)
    private boolean visibleToAdmin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampusNoticeStatus status = CampusNoticeStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampusNotice() {
        // Required by JPA.
    }

    /** A new DRAFT notice. */
    public CampusNotice(Content content) {
        apply(content);
    }

    /** What an admin writes: the text and who may see it. */
    public record Content(String title, String message, boolean visibleToStudent, boolean visibleToFaculty,
            boolean visibleToAdmin) {
    }

    /** Corrects a draft or published notice. An archived notice cannot change. */
    public void edit(Content content) {
        if (status == CampusNoticeStatus.ARCHIVED) {
            throw new IllegalStateException("An archived notice cannot be edited");
        }
        apply(content);
    }

    /** Publishes a draft and returns true; a published notice stays as it is and returns false. */
    public boolean publish(Instant at) {
        if (status == CampusNoticeStatus.ARCHIVED) {
            throw new IllegalStateException("An archived notice cannot be published");
        }
        if (status == CampusNoticeStatus.PUBLISHED) {
            return false;
        }
        status = CampusNoticeStatus.PUBLISHED;
        if (publishedAt == null) {
            publishedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        }
        return true;
    }

    /** Archives a draft or published notice and returns true; an archived notice stays as it is and returns false. */
    public boolean archive() {
        if (status == CampusNoticeStatus.ARCHIVED) {
            return false;
        }
        status = CampusNoticeStatus.ARCHIVED;
        return true;
    }

    /** Whether an account holding these role codes may see the notice once it is published. */
    public boolean isVisibleTo(Collection<String> roleCodes) {
        return (visibleToStudent && roleCodes.contains("STUDENT")) || (visibleToFaculty && roleCodes.contains("FACULTY"))
                || (visibleToAdmin && roleCodes.contains("ADMIN"));
    }

    private void apply(Content content) {
        Require.notNull(content, "content");
        if (!content.visibleToStudent() && !content.visibleToFaculty() && !content.visibleToAdmin()) {
            throw new IllegalArgumentException("a notice is visible to at least one role");
        }
        this.title = Require.text(content.title(), "title", MAX_TITLE_LENGTH);
        this.message = Require.text(content.message(), "message", MAX_MESSAGE_LENGTH);
        this.visibleToStudent = content.visibleToStudent();
        this.visibleToFaculty = content.visibleToFaculty();
        this.visibleToAdmin = content.visibleToAdmin();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isVisibleToStudent() {
        return visibleToStudent;
    }

    public boolean isVisibleToFaculty() {
        return visibleToFaculty;
    }

    public boolean isVisibleToAdmin() {
        return visibleToAdmin;
    }

    public CampusNoticeStatus getStatus() {
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
}

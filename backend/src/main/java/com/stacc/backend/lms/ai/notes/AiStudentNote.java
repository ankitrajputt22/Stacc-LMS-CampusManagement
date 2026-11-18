package com.stacc.backend.lms.ai.notes;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "ai_student_notes")
public class AiStudentNote {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_CONTENT_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiStudentNote() {
    }

    public AiStudentNote(Long lmsCourseId, Long userAccountId, String title, String content) {
        if (lmsCourseId == null || userAccountId == null) {
            throw new IllegalArgumentException("lmsCourseId and userAccountId are required");
        }
        this.lmsCourseId = lmsCourseId;
        this.userAccountId = userAccountId;
        edit(title, content);
    }

    public Long getId() {
        return id;
    }

    public Long getLmsCourseId() {
        return lmsCourseId;
    }

    public Long getUserAccountId() {
        return userAccountId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void edit(String title, String content) {
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.content = Require.text(content, "content", MAX_CONTENT_LENGTH);
    }
}

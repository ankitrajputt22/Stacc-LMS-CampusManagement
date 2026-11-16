package com.stacc.backend.lms.ai.notes;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The fingerprint of one student's notes in one course as last indexed (v0.13.6). Never their text. */
@Entity
@Table(name = "ai_student_note_indexes")
public class AiStudentNoteIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    protected AiStudentNoteIndex() {
        // Required by JPA.
    }

    public AiStudentNoteIndex(Long lmsCourseId, Long userAccountId, String contentFingerprint, Instant indexedAt) {
        this.lmsCourseId = lmsCourseId;
        this.userAccountId = userAccountId;
        indexed(contentFingerprint, indexedAt);
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void indexed(String contentFingerprint, Instant indexedAt) {
        this.contentFingerprint = contentFingerprint;
        this.indexedAt = indexedAt;
    }
}

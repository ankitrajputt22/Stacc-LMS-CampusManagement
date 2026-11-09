package com.stacc.backend.lms.message;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
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

/**
 * A private conversation between one student and the faculty of one LMS course. The student starts it; every faculty
 * member teaching the course may read and answer it. It is never deleted.
 */
@Entity
@Table(name = "lms_conversations")
public class LmsConversation {

    public static final int MAX_SUBJECT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_account_id", nullable = false, updatable = false)
    private UserAccount student;

    @Column(nullable = false, updatable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    protected LmsConversation() {
        // Required by JPA.
    }

    public LmsConversation(LmsCourse lmsCourse, UserAccount student, String subject, Instant now) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.student = Require.notNull(student, "student");
        this.subject = Require.text(subject, "subject", MAX_SUBJECT_LENGTH);
        this.createdAt = Require.notNull(now, "now");
        this.lastMessageAt = now;
    }

    public void messaged(Instant at) {
        this.lastMessageAt = at;
    }

    public Long getId() { return id; }
    public LmsCourse getLmsCourse() { return lmsCourse; }
    public UserAccount getStudent() { return student; }
    public String getSubject() { return subject; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
}

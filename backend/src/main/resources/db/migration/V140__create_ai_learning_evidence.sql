-- v0.15.5: learning evidence. One row per practice answer marked or flashcard reviewed: the student, the course, the page
-- it was about, the kind, and a score from 0 to 1. Numbers only, never the question or answer. Mastery and confidence
-- are worked out from these rows on every read and never stored. Only the student sees them, and may clear them for good.
CREATE TABLE ai_learning_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    user_account_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    kind VARCHAR(30) NOT NULL,
    score DECIMAL(4,3) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_learning_evidence_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_ai_learning_evidence_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_ai_learning_evidence_kind CHECK (kind IN ('PRACTICE_ANSWER', 'FLASHCARD_REVIEW')),
    CONSTRAINT ck_ai_learning_evidence_score CHECK (score >= 0 AND score <= 1),
    INDEX ix_ai_learning_evidence_student (user_account_id, lms_course_id, topic_id, created_at)
);

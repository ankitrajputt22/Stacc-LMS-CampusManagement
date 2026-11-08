-- v0.7.2: the question bank of an LMS course. A question keeps its own answers (options, a true/false answer, or
-- grading guidance), so the bank is for faculty only. Questions are archived, never deleted. text_format covers both
-- the question text and the explanation; answer guidance and options are plain text.
CREATE TABLE lms_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    question_text MEDIUMTEXT NOT NULL,
    explanation MEDIUMTEXT NULL,
    text_format VARCHAR(10) NOT NULL,
    answer_guidance TEXT NULL,
    true_false_answer BOOLEAN NULL,
    topic VARCHAR(100) NULL,
    difficulty VARCHAR(10) NOT NULL,
    marks DECIMAL(8,2) NOT NULL,
    bloom_level VARCHAR(20) NULL,
    lms_course_outcome_id BIGINT NULL,
    review_status VARCHAR(20) NOT NULL,
    practice_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_questions_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_questions_outcome FOREIGN KEY (lms_course_outcome_id) REFERENCES lms_course_outcomes (id),
    CONSTRAINT fk_lms_questions_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_questions_type CHECK (question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'DESCRIPTIVE')),
    CONSTRAINT ck_lms_questions_text_format CHECK (text_format IN ('PLAIN', 'FORMATTED')),
    CONSTRAINT ck_lms_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_lms_questions_marks CHECK (marks > 0),
    CONSTRAINT ck_lms_questions_review CHECK (review_status IN ('NEEDS_REVIEW', 'REVIEWED')),
    CONSTRAINT ck_lms_questions_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_lms_questions_practice CHECK (practice_allowed = FALSE OR review_status = 'REVIEWED'),
    CONSTRAINT ck_lms_questions_true_false CHECK ((question_type = 'TRUE_FALSE') = (true_false_answer IS NOT NULL)),
    CONSTRAINT ck_lms_questions_bloom
        CHECK (bloom_level IS NULL OR bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE')),
    INDEX ix_lms_questions_course (lms_course_id, status)
);

CREATE TABLE lms_question_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_question_id BIGINT NOT NULL,
    position INT NOT NULL,
    option_text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_question_options_position UNIQUE (lms_question_id, position),
    CONSTRAINT fk_lms_question_options_question FOREIGN KEY (lms_question_id) REFERENCES lms_questions (id),
    CONSTRAINT ck_lms_question_options_position CHECK (position > 0)
);

INSERT INTO permissions (code) VALUES ('LMS_QUESTION_BANK_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'LMS_QUESTION_BANK_MANAGE'));

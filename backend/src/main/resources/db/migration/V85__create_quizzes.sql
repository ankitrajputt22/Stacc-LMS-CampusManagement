-- v0.7.3: quizzes of an LMS course, built from its question bank. A quiz holds its settings; lms_quiz_questions links
-- bank questions to it in order, each with the marks it carries in this quiz. Quizzes are archived, never deleted.
-- Questions can be added, removed, reordered, and re-marked only while the quiz is a draft.
CREATE TABLE lms_quizzes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions MEDIUMTEXT NULL,
    instructions_format VARCHAR(10) NOT NULL,
    quiz_type VARCHAR(10) NOT NULL,
    time_limit_minutes INT NULL,
    available_from DATETIME(6) NULL,
    due_at DATETIME(6) NULL,
    available_until DATETIME(6) NULL,
    max_attempts INT NULL,
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options BOOLEAN NOT NULL DEFAULT FALSE,
    negative_mark_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    published_at DATETIME(6) NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_quizzes_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_quizzes_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_quizzes_format CHECK (instructions_format IN ('PLAIN', 'FORMATTED')),
    CONSTRAINT ck_lms_quizzes_type CHECK (quiz_type IN ('GRADED', 'PRACTICE')),
    CONSTRAINT ck_lms_quizzes_time_limit CHECK (time_limit_minutes IS NULL OR time_limit_minutes BETWEEN 1 AND 600),
    CONSTRAINT ck_lms_quizzes_attempts CHECK (max_attempts IS NULL OR max_attempts BETWEEN 1 AND 100),
    CONSTRAINT ck_lms_quizzes_negative CHECK (negative_mark_percent >= 0 AND negative_mark_percent <= 100),
    CONSTRAINT ck_lms_quizzes_window CHECK (available_from IS NULL OR available_until IS NULL OR available_from < available_until),
    CONSTRAINT ck_lms_quizzes_due_from CHECK (available_from IS NULL OR due_at IS NULL OR available_from <= due_at),
    CONSTRAINT ck_lms_quizzes_due_until CHECK (due_at IS NULL OR available_until IS NULL OR due_at <= available_until),
    CONSTRAINT ck_lms_quizzes_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_lms_quizzes_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    INDEX ix_lms_quizzes_course (lms_course_id, status)
);

CREATE TABLE lms_quiz_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_quiz_id BIGINT NOT NULL,
    lms_question_id BIGINT NOT NULL,
    position INT NOT NULL,
    marks DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_quiz_questions_question UNIQUE (lms_quiz_id, lms_question_id),
    CONSTRAINT fk_lms_quiz_questions_quiz FOREIGN KEY (lms_quiz_id) REFERENCES lms_quizzes (id),
    CONSTRAINT fk_lms_quiz_questions_question FOREIGN KEY (lms_question_id) REFERENCES lms_questions (id),
    CONSTRAINT ck_lms_quiz_questions_position CHECK (position > 0),
    CONSTRAINT ck_lms_quiz_questions_marks CHECK (marks > 0),
    INDEX ix_lms_quiz_questions_question (lms_question_id)
);

INSERT INTO permissions (code) VALUES ('LMS_QUIZ_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'LMS_QUIZ_MANAGE'));

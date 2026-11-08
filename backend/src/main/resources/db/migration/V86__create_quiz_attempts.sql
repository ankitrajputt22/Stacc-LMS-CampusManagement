-- v0.7.4: students take published quizzes. An attempt belongs to the student's official course enrollment and a quiz;
-- its deadline (from the time limit and the closing time) and the due date are fixed when it starts. Answers are saved
-- one question at a time while the attempt is open; chosen options are kept by their position in the question.
-- At most one attempt per quiz and enrollment is in progress (a unique generated column). Nothing is deleted.
CREATE TABLE lms_quiz_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_quiz_id BIGINT NOT NULL,
    course_enrollment_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    deadline_at DATETIME(6) NULL,
    due_at_snapshot DATETIME(6) NULL,
    submitted_at DATETIME(6) NULL,
    in_progress_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'IN_PROGRESS' THEN 1 END) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_quiz_attempts_number UNIQUE (lms_quiz_id, course_enrollment_id, attempt_number),
    CONSTRAINT uk_lms_quiz_attempts_in_progress UNIQUE (lms_quiz_id, course_enrollment_id, in_progress_marker),
    CONSTRAINT fk_lms_quiz_attempts_quiz FOREIGN KEY (lms_quiz_id) REFERENCES lms_quizzes (id),
    CONSTRAINT fk_lms_quiz_attempts_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT ck_lms_quiz_attempts_number CHECK (attempt_number > 0),
    CONSTRAINT ck_lms_quiz_attempts_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    CONSTRAINT ck_lms_quiz_attempts_submitted CHECK ((status = 'IN_PROGRESS') = (submitted_at IS NULL)),
    INDEX ix_lms_quiz_attempts_enrollment (course_enrollment_id)
);

CREATE TABLE lms_quiz_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_quiz_attempt_id BIGINT NOT NULL,
    lms_quiz_question_id BIGINT NOT NULL,
    true_false_answer BOOLEAN NULL,
    text_answer MEDIUMTEXT NULL,
    answered_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_quiz_answers_question UNIQUE (lms_quiz_attempt_id, lms_quiz_question_id),
    CONSTRAINT fk_lms_quiz_answers_attempt FOREIGN KEY (lms_quiz_attempt_id) REFERENCES lms_quiz_attempts (id),
    CONSTRAINT fk_lms_quiz_answers_question FOREIGN KEY (lms_quiz_question_id) REFERENCES lms_quiz_questions (id)
);

CREATE TABLE lms_quiz_answer_options (
    lms_quiz_answer_id BIGINT NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (lms_quiz_answer_id, position),
    CONSTRAINT fk_lms_quiz_answer_options_answer FOREIGN KEY (lms_quiz_answer_id) REFERENCES lms_quiz_answers (id),
    CONSTRAINT ck_lms_quiz_answer_options_position CHECK (position > 0)
);

INSERT INTO permissions (code) VALUES ('LMS_QUIZ_TAKE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'LMS_QUIZ_TAKE'));

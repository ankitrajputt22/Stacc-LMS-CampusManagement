-- v0.7.5: marking quiz attempts. Choice, true/false, and short answers are marked automatically, worked out from the
-- answer and the question on every read and never stored. A teacher's mark for one answer (needed for descriptive
-- answers, and a regrade for any other) is stored here, one per answer, replaced without history.
-- Each quiz also says when students see their results and whether the correct answers and explanations come with them.
ALTER TABLE lms_quizzes
    ADD COLUMN result_release VARCHAR(20) NOT NULL DEFAULT 'AFTER_MARKING',
    ADD COLUMN show_answers BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_lms_quizzes_result_release CHECK (result_release IN ('AFTER_MARKING', 'AFTER_DUE_DATE'));

CREATE TABLE lms_quiz_answer_marks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_quiz_answer_id BIGINT NOT NULL,
    marks DECIMAL(8,2) NOT NULL,
    feedback TEXT NULL,
    marked_by_account_id BIGINT NOT NULL,
    marked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_quiz_answer_marks_answer UNIQUE (lms_quiz_answer_id),
    CONSTRAINT fk_lms_quiz_answer_marks_answer FOREIGN KEY (lms_quiz_answer_id) REFERENCES lms_quiz_answers (id),
    CONSTRAINT fk_lms_quiz_answer_marks_marked_by FOREIGN KEY (marked_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_quiz_answer_marks_marks CHECK (marks >= 0)
);

INSERT INTO permissions (code) VALUES ('LMS_QUIZ_GRADE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'LMS_QUIZ_GRADE'));

-- Faculty grades of assignment attempts. A grade belongs to one attempt, never to a student's submission as
-- a whole, so a new attempt starts ungraded and earlier grades stay with their attempts. An attempt has at
-- most one grade, which can be revised: the marks and feedback change, the latest grader and time are kept,
-- and earlier values are not. max_marks_snapshot is the assignment's maximum when the attempt was first
-- graded and never changes. Feedback is plain text. Nothing is deleted.
CREATE TABLE lms_submission_grades (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    graded_by_faculty_profile_id BIGINT NOT NULL,
    marks_awarded DECIMAL(8,2) NOT NULL,
    max_marks_snapshot DECIMAL(8,2) NOT NULL,
    feedback TEXT NULL,
    graded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_submission_grades_attempt FOREIGN KEY (attempt_id) REFERENCES lms_submission_attempts (id),
    CONSTRAINT fk_lms_submission_grades_graded_by FOREIGN KEY (graded_by_faculty_profile_id) REFERENCES faculty_profiles (id),
    CONSTRAINT uk_lms_submission_grades_attempt UNIQUE (attempt_id),
    CONSTRAINT ck_lms_submission_grades_marks CHECK (marks_awarded >= 0 AND marks_awarded <= max_marks_snapshot),
    CONSTRAINT ck_lms_submission_grades_max CHECK (max_marks_snapshot > 0)
);

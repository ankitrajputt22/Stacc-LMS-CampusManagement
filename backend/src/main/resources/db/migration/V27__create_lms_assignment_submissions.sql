-- Students' work for LMS assignments. A submission is one student's record for one assignment, through
-- their official course enrollment, so no account or profile is stored again. Each hand-in is an attempt
-- that never changes; handing in again adds the next attempt. Each attempt keeps the deadline that applied
-- when it was made, so whether it was late never changes. Files are attachments of attempts: only their
-- details are here, the bytes are in private object storage under object_key. Nothing is deleted.
CREATE TABLE lms_assignment_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    course_enrollment_id BIGINT NOT NULL,
    latest_attempt_number INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_assignment_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES lms_assignments (id),
    CONSTRAINT fk_lms_assignment_submissions_course_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT uk_lms_assignment_submissions_assignment_enrollment UNIQUE (assignment_id, course_enrollment_id),
    CONSTRAINT ck_lms_assignment_submissions_latest_attempt CHECK (latest_attempt_number >= 0)
);

-- Text is plain text. MEDIUMTEXT, because 20,000 characters can need more bytes than TEXT holds.
CREATE TABLE lms_submission_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    text_content MEDIUMTEXT NULL,
    submitted_at DATETIME(6) NOT NULL,
    due_at_snapshot DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_submission_attempts_submission FOREIGN KEY (submission_id) REFERENCES lms_assignment_submissions (id),
    CONSTRAINT uk_lms_submission_attempts_submission_number UNIQUE (submission_id, attempt_number),
    CONSTRAINT ck_lms_submission_attempts_number CHECK (attempt_number >= 1)
);

CREATE TABLE lms_submission_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_submission_attachments_attempt FOREIGN KEY (attempt_id) REFERENCES lms_submission_attempts (id),
    CONSTRAINT uk_lms_submission_attachments_object_key UNIQUE (object_key),
    CONSTRAINT ck_lms_submission_attachments_size CHECK (size_bytes >= 0),
    INDEX idx_lms_submission_attachments_attempt (attempt_id)
);

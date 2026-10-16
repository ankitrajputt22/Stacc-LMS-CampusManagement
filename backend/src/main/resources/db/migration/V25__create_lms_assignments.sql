-- Assignments of LMS courses: what work must be done, by when, and for how many marks. Each belongs to the
-- course itself. They start as drafts and are archived, never deleted, and stay after their deadline.
-- published_at records the first publication and stays empty for drafts. Instructions are plain text,
-- MEDIUMTEXT because 20,000 characters can need more bytes than TEXT holds. due_at is a UTC moment.
CREATE TABLE lms_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions MEDIUMTEXT NOT NULL,
    due_at DATETIME(6) NOT NULL,
    max_marks DECIMAL(8,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_assignments_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT ck_lms_assignments_max_marks CHECK (max_marks > 0),
    INDEX idx_lms_assignments_course_status_due (lms_course_id, status, due_at)
);

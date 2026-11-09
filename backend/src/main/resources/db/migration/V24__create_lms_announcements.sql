-- Course-wide announcements of LMS courses. Each belongs to the course itself. They start as drafts and
-- are archived, never deleted. published_at records the first publication and stays empty for drafts.
-- The message is plain text.
CREATE TABLE lms_announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_announcements_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    INDEX idx_lms_announcements_course_status_published (lms_course_id, status, published_at)
);

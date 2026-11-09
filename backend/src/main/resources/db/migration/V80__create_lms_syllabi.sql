-- v0.6.4: one syllabus per LMS course, written as formatted text. Faculty edit a draft; publishing copies it to the
-- version students see. The first and the latest publication times are kept; there is no unpublish and no delete.
CREATE TABLE lms_syllabi (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    draft_content MEDIUMTEXT NOT NULL,
    draft_updated_at DATETIME(6) NOT NULL,
    published_content MEDIUMTEXT NULL,
    first_published_at DATETIME(6) NULL,
    last_published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_syllabi_course UNIQUE (lms_course_id),
    CONSTRAINT fk_lms_syllabi_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT ck_lms_syllabi_published CHECK (
        (published_content IS NULL AND first_published_at IS NULL AND last_published_at IS NULL)
        OR (published_content IS NOT NULL AND first_published_at IS NOT NULL AND last_published_at IS NOT NULL))
);

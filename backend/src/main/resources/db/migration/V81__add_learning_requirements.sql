-- v0.6.5: basic learning requirements. A module can require the module before it to be completed first; a page can be
-- required. A student's completion of a page is recorded once, against their official course enrollment, and never
-- changed or deleted. Opening a page records nothing.
ALTER TABLE lms_modules ADD COLUMN requires_previous BOOLEAN NOT NULL DEFAULT FALSE AFTER position;
ALTER TABLE lms_topics ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE AFTER position;

CREATE TABLE lms_page_completions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_enrollment_id BIGINT NOT NULL,
    lms_topic_id BIGINT NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_page_completions_enrollment_topic UNIQUE (course_enrollment_id, lms_topic_id),
    CONSTRAINT fk_lms_page_completions_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT fk_lms_page_completions_topic FOREIGN KEY (lms_topic_id) REFERENCES lms_topics (id),
    INDEX ix_lms_page_completions_topic (lms_topic_id)
);

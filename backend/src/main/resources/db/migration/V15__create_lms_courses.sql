-- The LMS learning space for one course offering. A course offering can have at most one.
CREATE TABLE lms_courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_offering_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_courses_course_offering FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id),
    CONSTRAINT uk_lms_courses_course_offering UNIQUE (course_offering_id)
);

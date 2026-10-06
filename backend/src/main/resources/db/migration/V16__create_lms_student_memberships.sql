-- The LMS-side membership of a student in an LMS course, based on one official course enrollment.
-- A course enrollment can have at most one membership. An LMS course can have many.
CREATE TABLE lms_student_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_enrollment_id BIGINT NOT NULL,
    lms_course_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_student_memberships_course_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT fk_lms_student_memberships_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT uk_lms_student_memberships_course_enrollment UNIQUE (course_enrollment_id)
);

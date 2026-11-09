-- The official record that a semester enrollment takes part in a course offering. Each pair can exist only once.
CREATE TABLE course_enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    semester_enrollment_id BIGINT NOT NULL,
    course_offering_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_enrollments_semester_enrollment FOREIGN KEY (semester_enrollment_id) REFERENCES semester_enrollments (id),
    CONSTRAINT fk_course_enrollments_course_offering FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id),
    CONSTRAINT uk_course_enrollments_enrollment_offering UNIQUE (semester_enrollment_id, course_offering_id)
);

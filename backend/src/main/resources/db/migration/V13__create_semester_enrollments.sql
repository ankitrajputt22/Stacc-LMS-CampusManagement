-- The official record that a student takes part in a semester. A student can be enrolled in a semester only once.
CREATE TABLE semester_enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_profile_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_semester_enrollments_student_profile FOREIGN KEY (student_profile_id) REFERENCES student_profiles (id),
    CONSTRAINT fk_semester_enrollments_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT uk_semester_enrollments_student_semester UNIQUE (student_profile_id, semester_id)
);

-- The official record that a faculty member teaches a course offering. A faculty member can
-- teach many offerings and an offering can have several faculty members, but each pair can exist
-- only once.
CREATE TABLE teaching_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    faculty_profile_id BIGINT NOT NULL,
    course_offering_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_teaching_assignments_faculty_profile FOREIGN KEY (faculty_profile_id) REFERENCES faculty_profiles (id),
    CONSTRAINT fk_teaching_assignments_course_offering FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id),
    CONSTRAINT uk_teaching_assignments_faculty_offering UNIQUE (faculty_profile_id, course_offering_id)
);

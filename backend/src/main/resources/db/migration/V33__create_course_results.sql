-- Official course results. A result belongs to one course enrollment: that student, in that semester
-- enrollment, in that exact course offering. The student, course, offering, semester, program, and session
-- are reached through the enrollment and are not stored again. It is recorded explicitly by faculty, never
-- calculated from LMS grades or attendance, and needs no LMS course. Nothing is deleted.
CREATE TABLE course_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_enrollment_id BIGINT NOT NULL,
    marks_obtained DECIMAL(8,2) NOT NULL,
    max_marks DECIMAL(8,2) NOT NULL,
    grade_code VARCHAR(20) NULL,
    remarks VARCHAR(2000) NULL,
    status VARCHAR(20) NOT NULL,
    recorded_by_faculty_profile_id BIGINT NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_results_course_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT fk_course_results_recorded_by FOREIGN KEY (recorded_by_faculty_profile_id) REFERENCES faculty_profiles (id),
    CONSTRAINT uk_course_results_course_enrollment UNIQUE (course_enrollment_id),
    CONSTRAINT ck_course_results_marks CHECK (marks_obtained >= 0 AND marks_obtained <= max_marks),
    CONSTRAINT ck_course_results_max_marks CHECK (max_marks > 0),
    CONSTRAINT ck_course_results_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_course_results_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

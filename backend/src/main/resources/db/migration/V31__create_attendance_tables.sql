-- Official class attendance. It belongs to the academic side: a session is one class meeting of one course
-- offering, never of an LMS course, so attendance works whether or not an LMS course exists. When a session
-- is created, the offering's currently enrolled students are copied into it as records, so a later
-- enrollment change never alters an earlier session. Statuses are stored as words. Nothing is deleted.
CREATE TABLE attendance_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_offering_id BIGINT NOT NULL,
    created_by_faculty_profile_id BIGINT NOT NULL,
    held_at DATETIME(6) NOT NULL,
    topic VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    finalized_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_sessions_course_offering FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id),
    CONSTRAINT fk_attendance_sessions_created_by FOREIGN KEY (created_by_faculty_profile_id) REFERENCES faculty_profiles (id),
    CONSTRAINT ck_attendance_sessions_status CHECK (status IN ('OPEN', 'FINALIZED')),
    CONSTRAINT ck_attendance_sessions_finalized CHECK ((status = 'FINALIZED') = (finalized_at IS NOT NULL))
);

-- A course offering's sessions are listed by when they were held.
CREATE INDEX ix_attendance_sessions_offering_held ON attendance_sessions (course_offering_id, held_at);

-- One record per student and session, through the student's official course enrollment. A record starts
-- UNMARKED, never ABSENT; who marked it last and when are kept once it is PRESENT or ABSENT.
CREATE TABLE attendance_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attendance_session_id BIGINT NOT NULL,
    course_enrollment_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    marked_by_faculty_profile_id BIGINT NULL,
    marked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_records_session FOREIGN KEY (attendance_session_id) REFERENCES attendance_sessions (id),
    CONSTRAINT fk_attendance_records_course_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT fk_attendance_records_marked_by FOREIGN KEY (marked_by_faculty_profile_id) REFERENCES faculty_profiles (id),
    CONSTRAINT uk_attendance_records_session_enrollment UNIQUE (attendance_session_id, course_enrollment_id),
    CONSTRAINT ck_attendance_records_status CHECK (status IN ('UNMARKED', 'PRESENT', 'ABSENT')),
    CONSTRAINT ck_attendance_records_marked CHECK (
        (status = 'UNMARKED' AND marked_by_faculty_profile_id IS NULL AND marked_at IS NULL)
        OR (status <> 'UNMARKED' AND marked_by_faculty_profile_id IS NOT NULL AND marked_at IS NOT NULL))
);

-- A student's own attendance is found through their course enrollments.
CREATE INDEX ix_attendance_records_course_enrollment ON attendance_records (course_enrollment_id);

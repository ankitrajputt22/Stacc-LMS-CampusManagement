-- v0.16.6: examinations. An exam session belongs to one semester (such as "End Semester Examinations — December
-- 2026"); its exams are course offerings of that semester with a date, times in the college's time zone, a hall, and
-- the hall's seats. Admins (EXAM_MANAGE) build the schedule as a draft, generate seat plans, and publish it once;
-- students (EXAM_SCHEDULE_VIEW_OWN) see the published exams of the offerings they are enrolled in, with their seats.
-- A seat plan only ever adds seats (under the exam's row lock), never past the hall's seats; nothing is deleted, and
-- an exam is removed with removed_at.
CREATE TABLE exam_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    semester_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_exam_sessions PRIMARY KEY (id),
    CONSTRAINT fk_exam_sessions_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT ck_exam_sessions_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    INDEX ix_exam_sessions_semester (semester_id)
);

CREATE TABLE exam_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_session_id BIGINT NOT NULL,
    course_offering_id BIGINT NOT NULL,
    exam_date DATE NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    hall VARCHAR(40) NOT NULL,
    hall_seats INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    live_key VARCHAR(41) AS (CASE WHEN removed_at IS NULL THEN CONCAT(exam_session_id, ':', course_offering_id) END) STORED,
    CONSTRAINT pk_exam_slots PRIMARY KEY (id),
    CONSTRAINT fk_exam_slots_session FOREIGN KEY (exam_session_id) REFERENCES exam_sessions (id),
    CONSTRAINT fk_exam_slots_offering FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id),
    CONSTRAINT ck_exam_slots_times CHECK (ends_at > starts_at),
    CONSTRAINT ck_exam_slots_seats CHECK (hall_seats BETWEEN 1 AND 1000),
    CONSTRAINT uq_exam_slots_live UNIQUE (live_key)
);

CREATE TABLE exam_seats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_slot_id BIGINT NOT NULL,
    course_enrollment_id BIGINT NOT NULL,
    seat_number INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_exam_seats PRIMARY KEY (id),
    CONSTRAINT fk_exam_seats_slot FOREIGN KEY (exam_slot_id) REFERENCES exam_slots (id),
    CONSTRAINT fk_exam_seats_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT uq_exam_seats_student UNIQUE (exam_slot_id, course_enrollment_id),
    CONSTRAINT uq_exam_seats_seat UNIQUE (exam_slot_id, seat_number)
);

INSERT INTO permissions (code) VALUES ('EXAM_SCHEDULE_VIEW_OWN'), ('EXAM_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'EXAM_SCHEDULE_VIEW_OWN' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'EXAM_MANAGE' WHERE r.name = 'ADMIN';

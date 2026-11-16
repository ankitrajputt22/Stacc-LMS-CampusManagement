-- v0.16.2: the class timetable. Each weekly slot belongs to one course offering, on a day of the week (1 Monday to 7
-- Sunday), between two times of day in the college's time zone, in a room, as a lecture, tutorial, or lab. Admins add
-- slots and remove them (kept with removed_at, never deleted); the server refuses a room or a class of students booked
-- twice at once, under the academic session's row lock. Students see the slots of the offerings they are enrolled in,
-- faculty those they teach.
CREATE TABLE class_timetable_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_offering_id BIGINT NOT NULL,
    day_of_week INT NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    room VARCHAR(40) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    CONSTRAINT pk_class_timetable_slots PRIMARY KEY (id),
    CONSTRAINT fk_class_timetable_slots_offering FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id),
    CONSTRAINT ck_class_timetable_slots_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_class_timetable_slots_times CHECK (ends_at > starts_at),
    CONSTRAINT ck_class_timetable_slots_kind CHECK (kind IN ('LECTURE', 'TUTORIAL', 'LAB')),
    INDEX ix_class_timetable_slots_offering (course_offering_id, removed_at)
);

INSERT INTO permissions (code) VALUES ('ACADEMIC_TIMETABLE_VIEW'), ('ACADEMIC_TIMETABLE_MANAGE'), ('TIMETABLE_VIEW_OWN');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('ACADEMIC_TIMETABLE_VIEW', 'ACADEMIC_TIMETABLE_MANAGE') WHERE r.name = 'ADMIN';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'TIMETABLE_VIEW_OWN' WHERE r.name IN ('STUDENT', 'FACULTY');

-- v0.3.3: course offerings per section, with a capacity and a registration status, and faculty roles on teaching
-- assignments managed by admins.

-- An offering may now name one section of its semester. section_key is the section's ID, or 0 for an offering of the
-- whole semester, so a course is offered at most once per semester and section. The new unique key starts with
-- course_id, so it also serves the course foreign key and the old key can be dropped after it.
ALTER TABLE course_offerings
    ADD COLUMN section_id BIGINT NULL AFTER semester_id,
    ADD COLUMN capacity INT NULL AFTER section_id,
    ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' AFTER capacity,
    ADD COLUMN section_key BIGINT GENERATED ALWAYS AS (COALESCE(section_id, 0)) STORED,
    ADD CONSTRAINT fk_course_offerings_section FOREIGN KEY (section_id) REFERENCES sections (id),
    ADD CONSTRAINT ck_course_offerings_capacity CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 1000),
    ADD CONSTRAINT uk_course_offerings_course_semester_section UNIQUE (course_id, semester_id, section_key);

ALTER TABLE course_offerings
    DROP INDEX uk_course_offerings_course_semester;

-- A faculty member's part in teaching an offering. An offering has at most one active primary faculty member, so of
-- each offering's existing assignments the earliest active one becomes primary and the others co-faculty.
ALTER TABLE teaching_assignments
    ADD COLUMN assignment_role VARCHAR(20) NOT NULL DEFAULT 'PRIMARY' AFTER course_offering_id;

UPDATE teaching_assignments assignment
LEFT JOIN (
    SELECT course_offering_id, MIN(id) AS primary_id
    FROM teaching_assignments
    WHERE status = 'ACTIVE'
    GROUP BY course_offering_id
) first_active ON first_active.course_offering_id = assignment.course_offering_id
SET assignment.assignment_role = 'CO_FACULTY'
WHERE first_active.primary_id IS NULL OR assignment.id <> first_active.primary_id;

-- Admins see and manage who teaches each offering. Roles are looked up by name.
INSERT INTO permissions (code) VALUES ('FACULTY_ASSIGNMENT_VIEW'), ('FACULTY_ASSIGNMENT_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'FACULTY_ASSIGNMENT_VIEW'));
INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE code = 'FACULTY_ASSIGNMENT_MANAGE'));

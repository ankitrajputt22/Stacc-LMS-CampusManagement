-- v0.3.1: the catalog details the ERP specification asks for, and one current academic session.

ALTER TABLE departments
    ADD COLUMN description VARCHAR(1000) NULL AFTER name;

-- What kind of degree a program leads to. Existing programs are undergraduate until an admin says otherwise.
ALTER TABLE programs
    ADD COLUMN degree_type VARCHAR(20) NOT NULL DEFAULT 'UNDERGRADUATE' AFTER name;

-- A short name such as DBMS, a plain-text description, and the kind of course. Existing courses are core courses
-- until an admin says otherwise.
ALTER TABLE courses
    ADD COLUMN short_name VARCHAR(20) NULL AFTER name,
    ADD COLUMN description VARCHAR(2000) NULL AFTER short_name,
    ADD COLUMN course_type VARCHAR(20) NOT NULL DEFAULT 'CORE' AFTER credits;

-- At most one session is the current one. current_marker is 1 only for the current session and NULL for every other,
-- so the unique key allows any number of non-current sessions but only one current session.
ALTER TABLE academic_sessions
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN current_marker TINYINT GENERATED ALWAYS AS (CASE WHEN is_current THEN 1 END) STORED,
    ADD CONSTRAINT uk_academic_sessions_one_current UNIQUE (current_marker);

-- Start with the active session that began last, if there is one.
UPDATE academic_sessions
SET is_current = TRUE
WHERE id = (
    SELECT id FROM (
        SELECT id FROM academic_sessions WHERE status = 'ACTIVE' ORDER BY start_date DESC, id DESC LIMIT 1
    ) AS latest_active
);

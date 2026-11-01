-- v0.3.2: student sections (A, B, ...) of a semester, and each semester enrollment's section.

-- A section is a group of students within one semester, which already says the program and the session. Its code is
-- unique within the semester. The capacity is optional; when set, it limits how many enrolled students it holds.
CREATE TABLE sections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    semester_id BIGINT NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(60) NULL,
    capacity INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sections_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT uk_sections_semester_code UNIQUE (semester_id, code),
    CONSTRAINT ck_sections_capacity CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 1000)
);

-- The section a student is placed in for this semester. Kept after the enrollment ends, as history.
ALTER TABLE semester_enrollments
    ADD COLUMN section_id BIGINT NULL AFTER semester_id,
    ADD CONSTRAINT fk_semester_enrollments_section FOREIGN KEY (section_id) REFERENCES sections (id),
    ADD INDEX ix_semester_enrollments_section_status (section_id, status);

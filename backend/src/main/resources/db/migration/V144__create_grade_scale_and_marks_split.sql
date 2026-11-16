-- v0.16.3: the college's grade scale and the split of marks. The scale is reference data (the 10-point scale, from O
-- to F) that maps a grade code to grade points and the lowest percentage for it; SGPA and CGPA are worked out from it
-- and course credits on read, never stored. A result may record internal and external marks with their maximums,
-- both parts or neither; their sums are then the marks obtained and the maximum.
CREATE TABLE grade_scale (
    grade_code VARCHAR(5) NOT NULL,
    grade_points DECIMAL(4,2) NOT NULL,
    min_percent DECIMAL(5,2) NOT NULL,
    passing BOOLEAN NOT NULL,
    position INT NOT NULL,
    CONSTRAINT pk_grade_scale PRIMARY KEY (grade_code),
    CONSTRAINT uq_grade_scale_position UNIQUE (position),
    CONSTRAINT ck_grade_scale_points CHECK (grade_points >= 0 AND grade_points <= 10),
    CONSTRAINT ck_grade_scale_percent CHECK (min_percent >= 0 AND min_percent <= 100)
);

INSERT INTO grade_scale (grade_code, grade_points, min_percent, passing, position) VALUES
    ('O', 10, 90, TRUE, 1),
    ('A+', 9, 80, TRUE, 2),
    ('A', 8, 70, TRUE, 3),
    ('B+', 7, 60, TRUE, 4),
    ('B', 6, 50, TRUE, 5),
    ('C', 5, 45, TRUE, 6),
    ('P', 4, 40, TRUE, 7),
    ('F', 0, 0, FALSE, 8);

ALTER TABLE course_results
    ADD COLUMN internal_marks DECIMAL(8,2) NULL,
    ADD COLUMN internal_max DECIMAL(8,2) NULL,
    ADD COLUMN external_marks DECIMAL(8,2) NULL,
    ADD COLUMN external_max DECIMAL(8,2) NULL,
    ADD CONSTRAINT ck_course_results_split CHECK (
        (internal_marks IS NULL AND internal_max IS NULL AND external_marks IS NULL AND external_max IS NULL)
        OR (internal_marks IS NOT NULL AND internal_max IS NOT NULL AND external_marks IS NOT NULL AND external_max IS NOT NULL));

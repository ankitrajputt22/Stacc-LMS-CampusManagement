-- v0.7.1: course learning outcomes (CO1, CO2, ...) numbered by the server per LMS course and never reused, archived
-- instead of deleted; and an assignment's alignment: an optional Bloom level and the outcomes it assesses.
CREATE TABLE lms_course_outcomes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    outcome_number INT NOT NULL,
    code VARCHAR(10) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_course_outcomes_course_number UNIQUE (lms_course_id, outcome_number),
    CONSTRAINT uk_lms_course_outcomes_course_code UNIQUE (lms_course_id, code),
    CONSTRAINT fk_lms_course_outcomes_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT ck_lms_course_outcomes_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_lms_course_outcomes_number CHECK (outcome_number > 0)
);

ALTER TABLE lms_assignments
    ADD COLUMN bloom_level VARCHAR(20) NULL AFTER max_marks,
    ADD CONSTRAINT ck_lms_assignments_bloom_level
        CHECK (bloom_level IS NULL OR bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE'));

CREATE TABLE lms_assignment_outcomes (
    lms_assignment_id BIGINT NOT NULL,
    lms_course_outcome_id BIGINT NOT NULL,
    PRIMARY KEY (lms_assignment_id, lms_course_outcome_id),
    CONSTRAINT fk_lms_assignment_outcomes_assignment FOREIGN KEY (lms_assignment_id) REFERENCES lms_assignments (id),
    CONSTRAINT fk_lms_assignment_outcomes_outcome FOREIGN KEY (lms_course_outcome_id) REFERENCES lms_course_outcomes (id),
    INDEX ix_lms_assignment_outcomes_outcome (lms_course_outcome_id)
);

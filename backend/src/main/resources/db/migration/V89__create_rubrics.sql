-- v0.7.7: rubrics. A rubric belongs to one LMS course and has criteria, each with maximum points and optional levels.
-- An assignment may use one rubric of its course whose total equals its maximum marks. Grading with the rubric keeps
-- the points and comments per criterion with the grade (an assessment), with each criterion's title and maximum as they
-- were. Rubrics are archived, never deleted; a rubric already used for grading keeps its criteria.
CREATE TABLE lms_rubrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_rubrics_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_rubrics_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_rubrics_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    INDEX ix_lms_rubrics_course (lms_course_id)
);

CREATE TABLE lms_rubric_criteria (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_rubric_id BIGINT NOT NULL,
    position INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    max_points DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_rubric_criteria_rubric FOREIGN KEY (lms_rubric_id) REFERENCES lms_rubrics (id),
    CONSTRAINT ck_lms_rubric_criteria_position CHECK (position > 0),
    CONSTRAINT ck_lms_rubric_criteria_points CHECK (max_points > 0)
);

CREATE TABLE lms_rubric_levels (
    lms_rubric_criterion_id BIGINT NOT NULL,
    position INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    points DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (lms_rubric_criterion_id, position),
    CONSTRAINT fk_lms_rubric_levels_criterion FOREIGN KEY (lms_rubric_criterion_id) REFERENCES lms_rubric_criteria (id),
    CONSTRAINT ck_lms_rubric_levels_points CHECK (points >= 0)
);

ALTER TABLE lms_assignments
    ADD COLUMN lms_rubric_id BIGINT NULL,
    ADD CONSTRAINT fk_lms_assignments_rubric FOREIGN KEY (lms_rubric_id) REFERENCES lms_rubrics (id);

CREATE TABLE lms_rubric_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_submission_grade_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_rubric_assessments_grade UNIQUE (lms_submission_grade_id),
    CONSTRAINT fk_lms_rubric_assessments_grade FOREIGN KEY (lms_submission_grade_id) REFERENCES lms_submission_grades (id)
);

CREATE TABLE lms_rubric_scores (
    lms_rubric_assessment_id BIGINT NOT NULL,
    position INT NOT NULL,
    lms_rubric_criterion_id BIGINT NOT NULL,
    criterion_title VARCHAR(200) NOT NULL,
    max_points DECIMAL(8,2) NOT NULL,
    points DECIMAL(8,2) NOT NULL,
    comment TEXT NULL,
    PRIMARY KEY (lms_rubric_assessment_id, position),
    CONSTRAINT fk_lms_rubric_scores_assessment FOREIGN KEY (lms_rubric_assessment_id) REFERENCES lms_rubric_assessments (id),
    CONSTRAINT fk_lms_rubric_scores_criterion FOREIGN KEY (lms_rubric_criterion_id) REFERENCES lms_rubric_criteria (id),
    CONSTRAINT ck_lms_rubric_scores_points CHECK (points >= 0 AND points <= max_points)
);

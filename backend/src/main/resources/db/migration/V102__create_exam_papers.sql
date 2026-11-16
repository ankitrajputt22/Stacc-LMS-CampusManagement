-- v0.7.9: exam blueprints and papers built from an LMS course's question bank, without AI. A blueprint plans a paper
-- as ordered rows (section, filters, how many questions, marks each); a paper holds bank questions in order with
-- their section and marks. Nothing is copied from the bank. Blueprints and papers are archived, never deleted; a
-- final paper keeps its questions, and a question in a final paper can no longer be edited in the bank.
CREATE TABLE lms_exam_blueprints (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_exam_blueprints_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_exam_blueprints_creator FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_exam_blueprints_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    INDEX ix_lms_exam_blueprints_course (lms_course_id)
);

CREATE TABLE lms_exam_blueprint_rows (
    lms_exam_blueprint_id BIGINT NOT NULL,
    position INT NOT NULL,
    section_title VARCHAR(100) NULL,
    question_type VARCHAR(20) NULL,
    difficulty VARCHAR(10) NULL,
    bloom_level VARCHAR(20) NULL,
    lms_course_outcome_id BIGINT NULL,
    topic VARCHAR(100) NULL,
    question_count INT NOT NULL,
    marks_each DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (lms_exam_blueprint_id, position),
    CONSTRAINT fk_lms_exam_blueprint_rows_blueprint FOREIGN KEY (lms_exam_blueprint_id) REFERENCES lms_exam_blueprints (id),
    CONSTRAINT fk_lms_exam_blueprint_rows_outcome FOREIGN KEY (lms_course_outcome_id) REFERENCES lms_course_outcomes (id),
    CONSTRAINT ck_lms_exam_blueprint_rows_position CHECK (position > 0),
    CONSTRAINT ck_lms_exam_blueprint_rows_count CHECK (question_count BETWEEN 1 AND 100),
    CONSTRAINT ck_lms_exam_blueprint_rows_marks CHECK (marks_each > 0)
);

CREATE TABLE lms_exam_papers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    lms_exam_blueprint_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT NULL,
    duration_minutes INT NULL,
    status VARCHAR(10) NOT NULL,
    finalized_at DATETIME(6) NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_exam_papers_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_exam_papers_blueprint FOREIGN KEY (lms_exam_blueprint_id) REFERENCES lms_exam_blueprints (id),
    CONSTRAINT fk_lms_exam_papers_creator FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_exam_papers_status CHECK (status IN ('DRAFT', 'FINAL', 'ARCHIVED')),
    CONSTRAINT ck_lms_exam_papers_duration CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 1440),
    INDEX ix_lms_exam_papers_course (lms_course_id)
);

CREATE TABLE lms_exam_paper_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_exam_paper_id BIGINT NOT NULL,
    lms_question_id BIGINT NOT NULL,
    position INT NOT NULL,
    section_title VARCHAR(100) NULL,
    marks DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_exam_paper_questions_paper FOREIGN KEY (lms_exam_paper_id) REFERENCES lms_exam_papers (id),
    CONSTRAINT fk_lms_exam_paper_questions_question FOREIGN KEY (lms_question_id) REFERENCES lms_questions (id),
    CONSTRAINT uq_lms_exam_paper_questions_question UNIQUE (lms_exam_paper_id, lms_question_id),
    CONSTRAINT ck_lms_exam_paper_questions_position CHECK (position > 0),
    CONSTRAINT ck_lms_exam_paper_questions_marks CHECK (marks > 0),
    INDEX ix_lms_exam_paper_questions_question (lms_question_id)
);

-- LMS_EXAM_PAPER_MANAGE: blueprints, papers, the print view, and Word export for courses one teaches. Roles by name.
INSERT INTO permissions (code) VALUES ('LMS_EXAM_PAPER_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'LMS_EXAM_PAPER_MANAGE' WHERE r.name = 'FACULTY';

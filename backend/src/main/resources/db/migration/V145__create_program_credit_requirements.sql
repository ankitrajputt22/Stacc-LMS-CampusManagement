-- v0.16.4: degree requirements. A program asks for a number of credits of each course type (core, electives, labs,
-- projects); together they are the degree's credits. Progress is worked out on read from published, passing results
-- and never stored. Admins set the requirements with the academic master permissions.
CREATE TABLE program_credit_requirements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    course_type VARCHAR(20) NOT NULL,
    required_credits DECIMAL(6,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_program_credit_requirements PRIMARY KEY (id),
    CONSTRAINT fk_program_credit_requirements_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT uq_program_credit_requirements UNIQUE (program_id, course_type),
    CONSTRAINT ck_program_credit_requirements_type CHECK (course_type IN ('CORE', 'ELECTIVE', 'LAB', 'PROJECT', 'AUDIT')),
    CONSTRAINT ck_program_credit_requirements_credits CHECK (required_credits > 0 AND required_credits <= 400)
);

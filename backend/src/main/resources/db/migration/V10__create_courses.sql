-- The permanent course catalog. A course is not tied to a programme, a semester, or a session.
CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    credits DECIMAL(4,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_courses_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT uk_courses_code UNIQUE (code),
    CONSTRAINT ck_courses_credits CHECK (credits > 0 AND credits <= 20.00)
);

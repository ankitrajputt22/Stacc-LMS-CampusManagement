-- Academic programmes. Each belongs to one department, and its code and name are unique within that department.
CREATE TABLE programs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    duration_semesters INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_programs_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT uk_programs_department_code UNIQUE (department_id, code),
    CONSTRAINT uk_programs_department_name UNIQUE (department_id, name),
    CONSTRAINT ck_programs_duration_semesters CHECK (duration_semesters BETWEEN 1 AND 20)
);

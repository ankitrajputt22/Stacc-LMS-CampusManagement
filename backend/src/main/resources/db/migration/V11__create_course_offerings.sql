-- One course offered in one semester. A course can be offered in many semesters, but only once in each.
CREATE TABLE course_offerings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_offerings_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_course_offerings_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT uk_course_offerings_course_semester UNIQUE (course_id, semester_id)
);

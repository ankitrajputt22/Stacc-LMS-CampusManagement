-- v0.7.6: the course gradebook. Assignment and graded-quiz columns are worked out from their own marks on every read;
-- nothing is copied. A grade item row records only what the gradebook itself owns: a manual item (title and maximum
-- marks) or the moment a column's grades were published to students. Manual marks are grade entries, one per item and
-- course enrollment. Nothing is deleted.
CREATE TABLE lms_grade_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    lms_assignment_id BIGINT NULL,
    lms_quiz_id BIGINT NULL,
    title VARCHAR(200) NULL,
    max_marks DECIMAL(8,2) NULL,
    published_at DATETIME(6) NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_grade_items_assignment UNIQUE (lms_assignment_id),
    CONSTRAINT uk_lms_grade_items_quiz UNIQUE (lms_quiz_id),
    CONSTRAINT fk_lms_grade_items_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    CONSTRAINT fk_lms_grade_items_assignment FOREIGN KEY (lms_assignment_id) REFERENCES lms_assignments (id),
    CONSTRAINT fk_lms_grade_items_quiz FOREIGN KEY (lms_quiz_id) REFERENCES lms_quizzes (id),
    CONSTRAINT fk_lms_grade_items_created_by FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_grade_items_type CHECK (item_type IN ('ASSIGNMENT', 'QUIZ', 'MANUAL')),
    CONSTRAINT ck_lms_grade_items_source CHECK ((item_type = 'ASSIGNMENT') = (lms_assignment_id IS NOT NULL)
        AND (item_type = 'QUIZ') = (lms_quiz_id IS NOT NULL)
        AND (item_type = 'MANUAL') = (title IS NOT NULL AND max_marks IS NOT NULL)),
    CONSTRAINT ck_lms_grade_items_marks CHECK (max_marks IS NULL OR max_marks > 0),
    INDEX ix_lms_grade_items_course (lms_course_id)
);

CREATE TABLE lms_grade_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_grade_item_id BIGINT NOT NULL,
    course_enrollment_id BIGINT NOT NULL,
    marks DECIMAL(8,2) NOT NULL,
    feedback TEXT NULL,
    graded_by_account_id BIGINT NOT NULL,
    graded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lms_grade_entries_student UNIQUE (lms_grade_item_id, course_enrollment_id),
    CONSTRAINT fk_lms_grade_entries_item FOREIGN KEY (lms_grade_item_id) REFERENCES lms_grade_items (id),
    CONSTRAINT fk_lms_grade_entries_enrollment FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments (id),
    CONSTRAINT fk_lms_grade_entries_graded_by FOREIGN KEY (graded_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_lms_grade_entries_marks CHECK (marks >= 0)
);

INSERT INTO permissions (code) VALUES ('LMS_GRADEBOOK_MANAGE'), ('LMS_GRADE_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
VALUES ((SELECT id FROM roles WHERE name = 'FACULTY'), (SELECT id FROM permissions WHERE code = 'LMS_GRADEBOOK_MANAGE')),
       ((SELECT id FROM roles WHERE name = 'STUDENT'), (SELECT id FROM permissions WHERE code = 'LMS_GRADE_VIEW'));

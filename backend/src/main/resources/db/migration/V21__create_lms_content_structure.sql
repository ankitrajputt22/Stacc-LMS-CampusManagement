-- The content of an LMS course: modules, and topics inside them. Both start as drafts and are
-- archived, never deleted, so their history is kept. Order comes from the position, then the ID;
-- positions are not unique, so a reorder never trips over a half-finished numbering.
CREATE TABLE lms_modules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_modules_lms_course FOREIGN KEY (lms_course_id) REFERENCES lms_courses (id),
    INDEX idx_lms_modules_course_position (lms_course_id, position)
);

-- Topic text is plain text. MEDIUMTEXT, because 20,000 characters can need more bytes than TEXT holds.
CREATE TABLE lms_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lms_module_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lms_topics_lms_module FOREIGN KEY (lms_module_id) REFERENCES lms_modules (id),
    INDEX idx_lms_topics_module_position (lms_module_id, position)
);

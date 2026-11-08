-- v0.6.6: what an LMS course's home shows (an overview, the modules, or the syllabus) and an optional welcome message in
-- formatted text. Official course details stay in the academic records and are never copied here.
ALTER TABLE lms_courses
    ADD COLUMN home_view VARCHAR(20) NOT NULL DEFAULT 'OVERVIEW' AFTER status,
    ADD COLUMN welcome_message MEDIUMTEXT NULL AFTER home_view,
    ADD CONSTRAINT ck_lms_courses_home_view CHECK (home_view IN ('OVERVIEW', 'MODULES', 'SYLLABUS'));

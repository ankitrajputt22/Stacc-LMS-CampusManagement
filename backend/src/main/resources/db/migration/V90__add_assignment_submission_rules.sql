-- v0.7.8: when an assignment takes work. Submissions open at available_from (empty: from publication) and are due at
-- due_at. Late work is accepted only when allow_late_submissions is true, and never after available_until (empty: no
-- final closing time). max_attempts limits each student's attempts (empty: unlimited). Existing assignments keep
-- today's behaviour: always open, late work accepted, unlimited attempts.
ALTER TABLE lms_assignments
    ADD COLUMN available_from DATETIME(6) NULL,
    ADD COLUMN available_until DATETIME(6) NULL,
    ADD COLUMN allow_late_submissions BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN max_attempts INT NULL,
    ADD CONSTRAINT ck_lms_assignments_opens CHECK (available_from IS NULL OR available_from <= due_at),
    ADD CONSTRAINT ck_lms_assignments_closes CHECK (available_until IS NULL OR available_until >= due_at),
    ADD CONSTRAINT ck_lms_assignments_attempts CHECK (max_attempts IS NULL OR max_attempts BETWEEN 1 AND 100);

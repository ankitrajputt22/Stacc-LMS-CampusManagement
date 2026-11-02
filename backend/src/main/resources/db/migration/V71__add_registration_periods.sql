-- v0.4.1: the period in which students register for a semester's course offerings, the last moment they may drop
-- one, and an optional credit limit per semester for each program.

-- Moments are stored in UTC. A semester without a period is not open for registration.
ALTER TABLE semesters
    ADD COLUMN registration_opens_at DATETIME(6) NULL AFTER semester_number,
    ADD COLUMN registration_closes_at DATETIME(6) NULL AFTER registration_opens_at,
    ADD COLUMN drop_deadline DATETIME(6) NULL AFTER registration_closes_at,
    ADD CONSTRAINT ck_semesters_registration_period CHECK (
        (registration_opens_at IS NULL AND registration_closes_at IS NULL AND drop_deadline IS NULL)
        OR (registration_opens_at IS NOT NULL AND registration_closes_at IS NOT NULL
            AND registration_closes_at > registration_opens_at
            AND (drop_deadline IS NULL OR drop_deadline >= registration_opens_at)));

-- Null means no limit.
ALTER TABLE programs
    ADD COLUMN max_credits_per_semester DECIMAL(5,2) NULL AFTER duration_semesters,
    ADD CONSTRAINT ck_programs_max_credits CHECK (
        max_credits_per_semester IS NULL OR (max_credits_per_semester > 0 AND max_credits_per_semester <= 60));

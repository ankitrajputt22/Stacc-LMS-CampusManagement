-- v0.11.6: event attendance. A registered attendee checks in near the event's time, by showing a pass code from the
-- pass service (EVENT_CHECK_IN) or by the organizer by college ID. Attending once is recorded on the registration;
-- certificate eligibility is worked out from it on read, never stored.
ALTER TABLE campus_event_registrations
    ADD COLUMN attended_at DATETIME(6) NULL,
    ADD COLUMN check_in_method VARCHAR(10) NULL,
    ADD COLUMN checked_in_by_account_id BIGINT NULL,
    ADD CONSTRAINT fk_campus_event_registrations_checked_in_by FOREIGN KEY (checked_in_by_account_id) REFERENCES user_accounts (id),
    ADD CONSTRAINT ck_campus_event_registrations_method CHECK (check_in_method IS NULL OR check_in_method IN ('MANUAL', 'PASS')),
    ADD CONSTRAINT ck_campus_event_registrations_attended CHECK ((attended_at IS NULL) = (check_in_method IS NULL));

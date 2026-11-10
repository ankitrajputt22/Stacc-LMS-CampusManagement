-- v0.11.8: trials. An event's managers mark it as a trial before it starts, and it stays one. Once it has started they
-- record Selected or Not selected for each registered attendee; the outcome lives on the registration and may be
-- corrected, keeping who decided last and when.
ALTER TABLE campus_events
    ADD COLUMN trial BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE campus_event_registrations
    ADD COLUMN trial_outcome VARCHAR(20) NULL,
    ADD COLUMN trial_decided_at DATETIME(6) NULL,
    ADD COLUMN trial_decided_by_account_id BIGINT NULL,
    ADD CONSTRAINT fk_campus_event_registrations_trial_by FOREIGN KEY (trial_decided_by_account_id) REFERENCES user_accounts (id),
    ADD CONSTRAINT ck_campus_event_registrations_trial CHECK (trial_outcome IS NULL OR trial_outcome IN ('SELECTED', 'NOT_SELECTED')),
    ADD CONSTRAINT ck_campus_event_registrations_trial_decided CHECK ((trial_outcome IS NULL) = (trial_decided_at IS NULL));

-- The password lifecycle of an account: whether the person must choose a new password before doing anything else
-- (after the college issues a temporary one), when the password last changed, and when a temporary password stops
-- working. No password or hash history is kept.
ALTER TABLE user_accounts
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at DATETIME(6) NULL,
    ADD COLUMN temporary_password_expires_at DATETIME(6) NULL;

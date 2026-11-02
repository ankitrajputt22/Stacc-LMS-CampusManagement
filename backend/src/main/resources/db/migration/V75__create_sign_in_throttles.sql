-- v0.2.4: failed sign-ins counted per login ID and per network address, so repeated guessing gets slower and slower.
-- Accounts are never locked. Only a SHA-256 hash of each key is stored, never a login ID or an address.
CREATE TABLE sign_in_throttles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope VARCHAR(10) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    failures INT NOT NULL DEFAULT 0,
    first_failure_at DATETIME(6) NULL,
    blocked_until DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sign_in_throttles_scope_key UNIQUE (scope, key_hash),
    CONSTRAINT ck_sign_in_throttles_scope CHECK (scope IN ('ACCOUNT', 'ADDRESS')),
    CONSTRAINT ck_sign_in_throttles_failures CHECK (failures >= 0)
);

-- The person behind an account: the name the college records, an optional preferred name, and contact details. It is
-- kept apart from the login account (user_accounts) and from the academic student and faculty profiles. At most one
-- per account. The official photo's bytes live in private object storage; only its server-made key and type are here.
CREATE TABLE person_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    preferred_name VARCHAR(80) NULL,
    email VARCHAR(254) NULL,
    phone VARCHAR(20) NULL,
    photo_object_key VARCHAR(255) NULL,
    photo_content_type VARCHAR(50) NULL,
    photo_updated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_person_profiles_user_account UNIQUE (user_account_id),
    CONSTRAINT uk_person_profiles_email UNIQUE (email),
    CONSTRAINT fk_person_profiles_user_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

-- v0.16.1: the ERP profile. A person keeps one mailing address and up to three emergency contacts on their own
-- profile. Like the phone number, these are contact details: shown only to the person, never in tokens, URLs, or
-- logs. The person may remove a contact they added.
ALTER TABLE person_profiles ADD COLUMN mailing_address VARCHAR(500) NULL;

CREATE TABLE person_emergency_contacts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_profile_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    relation VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_person_emergency_contacts PRIMARY KEY (id),
    CONSTRAINT fk_person_emergency_contacts_profile FOREIGN KEY (person_profile_id) REFERENCES person_profiles (id),
    INDEX ix_person_emergency_contacts_profile (person_profile_id)
);


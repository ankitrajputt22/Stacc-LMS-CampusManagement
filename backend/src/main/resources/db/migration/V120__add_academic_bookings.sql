-- v0.12.5: academic bookings on the facilities engine. Facilities may also be classrooms and equipment, and the campus
-- office sets weekly class hours per facility (day of week and times in the college's time zone). New bookings that
-- overlap class hours are refused; removed class hours are kept with their removal time.
ALTER TABLE campus_facilities DROP CHECK ck_campus_facilities_kind;
ALTER TABLE campus_facilities
    ADD CONSTRAINT ck_campus_facilities_kind CHECK (kind IN ('ROOM', 'CLASSROOM', 'LAB', 'EQUIPMENT', 'HALL', 'COURT', 'GROUND', 'OTHER'));

CREATE TABLE campus_facility_class_hours (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_id BIGINT NOT NULL,
    day_of_week INT NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    label VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    CONSTRAINT pk_campus_facility_class_hours PRIMARY KEY (id),
    CONSTRAINT fk_campus_facility_class_hours_facility FOREIGN KEY (facility_id) REFERENCES campus_facilities (id),
    CONSTRAINT ck_campus_facility_class_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_campus_facility_class_hours_times CHECK (ends_at > starts_at),
    INDEX ix_campus_facility_class_hours_facility (facility_id, day_of_week)
);

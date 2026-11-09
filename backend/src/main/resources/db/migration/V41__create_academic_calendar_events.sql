-- Official academic calendar events: dated holidays, examination weeks, registration windows, and other academic
-- dates. Each belongs to one academic session; semester_id is empty for an event across the whole session, or names
-- one semester of that same session, which the application checks. Dates are plain days with no time zone, and the
-- end date is included. Events start as drafts and are cancelled, never deleted. published_at records the first
-- publication and stays set after cancelling, so a cancelled event that was published remains visible as cancelled.
-- Assignment deadlines are not stored here: they stay on lms_assignments.
CREATE TABLE academic_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    academic_session_id BIGINT NOT NULL,
    semester_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    category VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_academic_calendar_events_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_academic_calendar_events_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT ck_academic_calendar_events_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_academic_calendar_events_category
        CHECK (category IN ('ACADEMIC', 'HOLIDAY', 'EXAM', 'REGISTRATION', 'OTHER')),
    CONSTRAINT ck_academic_calendar_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT ck_academic_calendar_events_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT ck_academic_calendar_events_draft CHECK (status <> 'DRAFT' OR published_at IS NULL),
    CONSTRAINT ck_academic_calendar_events_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    INDEX idx_academic_calendar_events_session_dates (academic_session_id, start_date, end_date),
    INDEX idx_academic_calendar_events_semester_dates (semester_id, start_date, end_date),
    INDEX idx_academic_calendar_events_status_dates (status, start_date, end_date)
);

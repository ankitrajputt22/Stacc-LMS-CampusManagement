-- v0.16.5: student requests of the ERP. A student asks for medical leave (dates and a reason), a correction of a
-- finalized absence of their own, or a revaluation of a published result of their own. The ERP office decides each
-- once, approving or rejecting with a comment (a rejection always has one), under the request's row lock; an approved
-- correction marks the record present, and an approved revaluation applies the new marks to the result. Requests are
-- cancelled by the student while still submitted, and never deleted. One open request per record or result.
CREATE TABLE student_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(30) NOT NULL,
    requester_account_id BIGINT NOT NULL,
    attendance_record_id BIGINT NULL,
    course_result_id BIGINT NULL,
    from_date DATE NULL,
    to_date DATE NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    outcome VARCHAR(200) NULL,
    decision_comment VARCHAR(1000) NULL,
    decided_by_account_id BIGINT NULL,
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    open_key VARCHAR(60) AS (CASE WHEN status = 'SUBMITTED' AND kind <> 'MEDICAL_LEAVE'
        THEN CONCAT(kind, ':', COALESCE(attendance_record_id, course_result_id)) END) STORED,
    CONSTRAINT pk_student_requests PRIMARY KEY (id),
    CONSTRAINT fk_student_requests_requester FOREIGN KEY (requester_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_student_requests_record FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (id),
    CONSTRAINT fk_student_requests_result FOREIGN KEY (course_result_id) REFERENCES course_results (id),
    CONSTRAINT fk_student_requests_decided_by FOREIGN KEY (decided_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_student_requests_kind CHECK (kind IN ('MEDICAL_LEAVE', 'ATTENDANCE_CORRECTION', 'REVALUATION')),
    CONSTRAINT ck_student_requests_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT uq_student_requests_open UNIQUE (open_key),
    INDEX ix_student_requests_requester (requester_account_id, created_at),
    INDEX ix_student_requests_status (status, created_at)
);

INSERT INTO permissions (code) VALUES ('ERP_REQUEST_SUBMIT'), ('ERP_REQUEST_MANAGE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ERP_REQUEST_SUBMIT' WHERE r.name = 'STUDENT';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ERP_REQUEST_MANAGE' WHERE r.name = 'ADMIN';

ALTER TABLE notifications DROP CHECK ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT_PUBLISHED',
    'ASSIGNMENT_GRADED', 'QUIZ_PUBLISHED', 'ANNOUNCEMENT_PUBLISHED', 'ACADEMIC_CALENDAR_PUBLISHED',
    'ACADEMIC_CALENDAR_CANCELLED', 'ATTENDANCE_UPDATED', 'RESULT_PUBLISHED', 'RESULT_UPDATED', 'HELPDESK_REPLY',
    'HELPDESK_STATUS_CHANGED', 'HELPDESK_ASSIGNED', 'APPROVAL_ACTION_REQUIRED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'APPROVAL_CANCELLED', 'HOSTEL_ALLOCATED', 'HOSTEL_VACATED', 'HOSTEL_EMERGENCY', 'CAMPUS_NOTICE_PUBLISHED',
    'CAMPUS_SERVICE_READY', 'CAMPUS_SERVICE_COMPLETED', 'GROUP_ANNOUNCEMENT', 'ERP_REQUEST_DECIDED'));

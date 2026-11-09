package com.stacc.backend.report;

import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionStatus;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.result.CourseResultStatus;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.campus.services.CampusServiceRequestRepository;
import com.stacc.backend.campus.services.CampusServiceRequestStatus;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelAllocationStatus;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;

/** Grouped count rows, as the repositories return them, for the report tests. */
final class ReportTestRows {

    private ReportTestRows() {
    }

    static CourseEnrollmentRepository.OfferingStatusCount enrollment(long offeringId, CourseEnrollmentStatus status, long total) {
        return new CourseEnrollmentRepository.OfferingStatusCount() {
            public Long getCourseOfferingId() { return offeringId; }
            public CourseEnrollmentStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static CourseEnrollmentRepository.StatusCount courseEnrollment(CourseEnrollmentStatus status, long total) {
        return new CourseEnrollmentRepository.StatusCount() {
            public CourseEnrollmentStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static SemesterEnrollmentRepository.StatusCount semesterEnrollment(SemesterEnrollmentStatus status, long total) {
        return new SemesterEnrollmentRepository.StatusCount() {
            public SemesterEnrollmentStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static AttendanceSessionRepository.OfferingStatusCount session(long offeringId, AttendanceSessionStatus status, long total) {
        return new AttendanceSessionRepository.OfferingStatusCount() {
            public Long getCourseOfferingId() { return offeringId; }
            public AttendanceSessionStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static AttendanceRecordRepository.RecordStatusCount record(AttendanceStatus status, long total) {
        return new AttendanceRecordRepository.RecordStatusCount() {
            public AttendanceStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static CourseResultRepository.OfferingStatusCount result(long offeringId, CourseResultStatus status, long total) {
        return new CourseResultRepository.OfferingStatusCount() {
            public Long getCourseOfferingId() { return offeringId; }
            public CourseResultStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static LmsAssignmentRepository.CourseStatusCount assignment(long lmsCourseId, LmsContentStatus status, long total) {
        return new LmsAssignmentRepository.CourseStatusCount() {
            public Long getLmsCourseId() { return lmsCourseId; }
            public LmsContentStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static HelpdeskTicketRepository.StatusCount ticket(HelpdeskTicketStatus status, long total) {
        return new HelpdeskTicketRepository.StatusCount() {
            public HelpdeskTicketStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static HostelAllocationRepository.StatusCount allocation(HostelAllocationStatus status, long total) {
        return new HostelAllocationRepository.StatusCount() {
            public HostelAllocationStatus getStatus() { return status; }
            public Long getTotal() { return total; }
        };
    }

    static CampusServiceRequestRepository.StatusCount request(CampusServiceRequestStatus status,
            ApprovalRequestStatus approval, long total) {
        return new CampusServiceRequestRepository.StatusCount() {
            public CampusServiceRequestStatus getStatus() { return status; }
            public ApprovalRequestStatus getApprovalStatus() { return approval; }
            public Long getTotal() { return total; }
        };
    }
}

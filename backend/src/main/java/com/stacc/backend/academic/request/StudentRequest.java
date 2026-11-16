package com.stacc.backend.academic.request;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A student's request to the ERP office (v0.16.5), decided once and never deleted. */
@Entity
@Table(name = "student_requests")
public class StudentRequest {

    public enum Kind {
        MEDICAL_LEAVE("MED"), ATTENDANCE_CORRECTION("COR"), REVALUATION("REV");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    public enum Status { SUBMITTED, APPROVED, REJECTED, CANCELLED }

    public static final int MAX_REASON = 1000;
    public static final int MAX_COMMENT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_account_id", nullable = false, updatable = false)
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id", updatable = false)
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_result_id", updatable = false)
    private CourseResult courseResult;

    @Column(name = "from_date", updatable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", updatable = false)
    private LocalDate toDate;

    @Column(nullable = false, length = MAX_REASON, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(length = 200)
    private String outcome;

    @Column(name = "decision_comment", length = MAX_COMMENT)
    private String decisionComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_account_id")
    private UserAccount decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentRequest() {
    }

    private StudentRequest(Kind kind, UserAccount requester, String reason, Instant at) {
        this.kind = kind;
        this.requester = requester;
        this.reason = reason;
        this.createdAt = at;
    }

    public static StudentRequest medicalLeave(UserAccount requester, LocalDate from, LocalDate to, String reason, Instant at) {
        StudentRequest request = new StudentRequest(Kind.MEDICAL_LEAVE, requester, reason, at);
        request.fromDate = from;
        request.toDate = to;
        return request;
    }

    public static StudentRequest correction(UserAccount requester, AttendanceRecord record, String reason, Instant at) {
        StudentRequest request = new StudentRequest(Kind.ATTENDANCE_CORRECTION, requester, reason, at);
        request.attendanceRecord = record;
        return request;
    }

    public static StudentRequest revaluation(UserAccount requester, CourseResult result, String reason, Instant at) {
        StudentRequest request = new StudentRequest(Kind.REVALUATION, requester, reason, at);
        request.courseResult = result;
        return request;
    }

    /** The number people quote, such as MED-000024. */
    public String reference() {
        return kind.prefix() + "-" + String.format("%06d", id);
    }

    public void decide(boolean approve, UserAccount by, String comment, String outcome, Instant at) {
        if (status != Status.SUBMITTED) {
            throw new IllegalStateException("Only a submitted request is decided.");
        }
        this.status = approve ? Status.APPROVED : Status.REJECTED;
        this.decidedBy = by;
        this.decisionComment = comment;
        this.outcome = outcome;
        this.decidedAt = at;
    }

    public void cancel(Instant at) {
        if (status != Status.SUBMITTED) {
            throw new IllegalStateException("Only a submitted request is cancelled.");
        }
        this.status = Status.CANCELLED;
        this.decidedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public AttendanceRecord getAttendanceRecord() {
        return attendanceRecord;
    }

    public CourseResult getCourseResult() {
        return courseResult;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

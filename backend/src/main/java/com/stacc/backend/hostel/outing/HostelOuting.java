package com.stacc.backend.hostel.outing;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.student.StudentProfile;
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

/**
 * A hostel resident's request to go out: a gate pass or leave, with its window and reason. Its approval lives in the
 * approval engine and is never copied here. {@code returnedAt} is set when the gate records the student coming back.
 */
@Entity
@Table(name = "hostel_outings")
public class HostelOuting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false, updatable = false)
    private StudentProfile studentProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private OutingKind kind;

    @Column(name = "leave_from", nullable = false, updatable = false)
    private Instant leaveFrom;

    @Column(name = "leave_until", nullable = false, updatable = false)
    private Instant leaveUntil;

    @Column(nullable = false, updatable = false, length = 1000)
    private String reason;

    @Column(length = 200, updatable = false)
    private String destination;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HostelOuting() {
        // Required by JPA.
    }

    public HostelOuting(StudentProfile studentProfile, OutingKind kind, Instant leaveFrom, Instant leaveUntil, String reason,
            String destination, Instant now) {
        this.studentProfile = Require.notNull(studentProfile, "studentProfile");
        this.kind = Require.notNull(kind, "kind");
        this.leaveFrom = Require.notNull(leaveFrom, "from");
        this.leaveUntil = Require.notNull(leaveUntil, "until");
        if (!leaveUntil.isAfter(leaveFrom)) {
            throw new IllegalArgumentException("the return must be after leaving");
        }
        if (java.time.Duration.between(leaveFrom, leaveUntil).compareTo(kind.longest()) > 0) {
            throw new IllegalArgumentException(kind == OutingKind.GATE_PASS ? "a gate pass lasts at most 24 hours"
                    : "leave lasts at most 60 days; ask for an extension later");
        }
        this.reason = Require.text(reason, "reason", 1000);
        this.destination = Require.optionalText(destination, "destination", 200);
        this.createdAt = Require.notNull(now, "now");
    }

    public void linkApproval(Long approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public void returned(Instant at) {
        if (returnedAt == null) {
            returnedAt = at;
        }
    }

    public Long getId() { return id; }
    public StudentProfile getStudentProfile() { return studentProfile; }
    public OutingKind getKind() { return kind; }
    public Instant getLeaveFrom() { return leaveFrom; }
    public Instant getLeaveUntil() { return leaveUntil; }
    public String getReason() { return reason; }
    public String getDestination() { return destination; }
    public Long getApprovalRequestId() { return approvalRequestId; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

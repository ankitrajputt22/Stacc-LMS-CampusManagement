package com.stacc.backend.hostel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.student.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A student's application for a hostel place in one academic session. Its approval is one request of the shared
 * approval engine, and its status is read from that request; the application keeps no status of its own. Applications
 * are never deleted.
 */
@Entity
@Table(name = "hostel_applications")
public class HostelApplication {

    public static final int MAX_NOTE_LENGTH = 3000;
    public static final String NUMBER_FORMAT = "HST-%06d";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Empty only inside the transaction that creates the application, until its ID is known.
    @Column(name = "application_number", unique = true, length = 20)
    private String applicationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false, updatable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_session_id", nullable = false, updatable = false)
    private AcademicSession academicSession;

    // Set once, in the transaction that creates the application, right after the approval starts.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", unique = true)
    private ApprovalRequest approvalRequest;

    @Column(length = MAX_NOTE_LENGTH)
    private String note;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostelApplication() {
        // Required by JPA.
    }

    public HostelApplication(StudentProfile studentProfile, AcademicSession academicSession, String note,
            Instant submittedAt) {
        this.studentProfile = Require.notNull(studentProfile, "studentProfile");
        this.academicSession = Require.notNull(academicSession, "academicSession");
        this.note = Require.optionalText(note, "note", MAX_NOTE_LENGTH);
        this.submittedAt = Require.notNull(submittedAt, "submittedAt").truncatedTo(ChronoUnit.MICROS);
    }

    /** The number of an application with this ID, such as HST-000042. */
    public static String numberFor(long id) {
        return NUMBER_FORMAT.formatted(id);
    }

    /** Gives the saved application its number, once. */
    public void assignNumber() {
        if (applicationNumber == null) {
            applicationNumber = numberFor(Require.notNull(id, "id"));
        }
    }

    /** Links the approval request that was started for this application, once. */
    public void linkApproval(ApprovalRequest request) {
        if (approvalRequest != null) {
            throw new IllegalStateException("The application already has an approval request");
        }
        this.approvalRequest = Require.notNull(request, "approvalRequest");
    }

    /** The approval status, always read from the approval request. */
    public ApprovalRequestStatus approvalStatus() {
        return approvalRequest == null ? null : approvalRequest.getStatus();
    }

    public Long getId() {
        return id;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public AcademicSession getAcademicSession() {
        return academicSession;
    }

    public ApprovalRequest getApprovalRequest() {
        return approvalRequest;
    }

    public String getNote() {
        return note;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

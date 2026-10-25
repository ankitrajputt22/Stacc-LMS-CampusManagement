package com.stacc.backend.hostel;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalRequestRepository;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelRequests.CreateHostelApplicationRequest;
import com.stacc.backend.hostel.HostelResponses.EligibleHostelSession;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationDetail;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Students' hostel applications: which sessions they may apply for, applying, and cancelling a pending application.
 * Approval is the shared approval engine's: applying starts a request with the {@code HOSTEL_APPLICATION} workflow and
 * cancelling cancels it there, so this service never changes approval state itself. The student always comes from the
 * access token.
 */
@Service
@Transactional
public class HostelApplicationService {

    public static final String WORKFLOW_CODE = "HOSTEL_APPLICATION";
    public static final String SOURCE_TYPE = "HOSTEL_APPLICATION";
    static final String NOT_FOUND = "Hostel application not found.";
    static final String NO_PROFILE = "You need an active student profile to apply for hostel.";
    static final String NOT_ENROLLED = "You can apply only for an academic session you are enrolled in.";
    static final String DUPLICATE = "You already have a pending or approved hostel application for this academic session.";
    static final String CANNOT_CANCEL = "This hostel application cannot be cancelled.";
    static final String CLOSED = "Hostel applications are not open at the moment.";

    private final HostelApplicationRepository applications;
    private final HostelAllocationRepository allocations;
    private final StudentProfileRepository studentProfiles;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final AcademicSessionRepository sessions;
    private final ApprovalWorkflowService approvals;
    private final ApprovalRequestRepository approvalRequests;
    private final HostelQueryService queries;
    private final Clock clock;

    public HostelApplicationService(HostelApplicationRepository applications, HostelAllocationRepository allocations,
            StudentProfileRepository studentProfiles, SemesterEnrollmentRepository semesterEnrollments,
            AcademicSessionRepository sessions, ApprovalWorkflowService approvals,
            ApprovalRequestRepository approvalRequests, HostelQueryService queries, Clock clock) {
        this.applications = applications;
        this.allocations = allocations;
        this.studentProfiles = studentProfiles;
        this.semesterEnrollments = semesterEnrollments;
        this.sessions = sessions;
        this.approvals = approvals;
        this.approvalRequests = approvalRequests;
        this.queries = queries;
        this.clock = clock;
    }

    /**
     * The sessions the student may apply for: those with at least one ENROLLED semester enrollment, the latest first,
     * marked when a pending or approved application exists. No LMS course, attendance, or result is needed. Empty
     * without an active student profile.
     */
    @Transactional(readOnly = true)
    public List<EligibleHostelSession> eligibleSessions(Long accountId) {
        StudentProfile profile = studentProfiles.findByUserAccountId(accountId).orElse(null);
        if (profile == null || profile.getStatus() != StudentProfileStatus.ACTIVE) {
            return List.of();
        }
        Set<Long> current = new HashSet<>(applications.findSessionIdsWithCurrentApplication(profile.getId()));
        return semesterEnrollments.findSessionsOfStudentWithStatus(profile.getId(), SemesterEnrollmentStatus.ENROLLED)
                .stream()
                .map(session -> new EligibleHostelSession(session.getId(), session.getCode(), session.getStartDate(),
                        session.getEndDate(), session.getStatus().name(), current.contains(session.getId())))
                .toList();
    }

    /**
     * Applies for a session and starts its approval, in one transaction. The student profile is locked first, so two
     * applications of the same student take turns and the duplicate check sees the other's result.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HostelApplicationDetail apply(Long accountId, CreateHostelApplicationRequest request) {
        StudentProfile profile = studentProfiles.findByUserAccountIdForUpdate(accountId)
                .filter(found -> found.getStatus() == StudentProfileStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, NO_PROFILE));
        AcademicSession session = (request.academicSessionId() == null ? null
                : sessions.findById(request.academicSessionId()).orElse(null));
        if (session == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Academic session not found.");
        }
        if (!semesterEnrollments.existsInSessionWithStatus(profile.getId(), session.getId(),
                SemesterEnrollmentStatus.ENROLLED)) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_ENROLLED);
        }
        if (applications.existsCurrent(profile.getId(), session.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE);
        }
        HostelApplication application;
        try {
            application = new HostelApplication(profile, session, request.note(), clock.instant());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The application is not valid: " + invalid.getMessage() + ".");
        }
        application = applications.saveAndFlush(application);
        application.assignNumber();
        ApprovalStartResult started;
        try {
            started = approvals.startApproval(new StartApprovalCommand(WORKFLOW_CODE, accountId, SOURCE_TYPE,
                    String.valueOf(application.getId()),
                    "Hostel application " + application.getApplicationNumber() + " for " + session.getCode()));
        } catch (ApiException unavailable) {
            // The workflow is missing or inactive: nothing is saved, and the student is told plainly.
            throw new ApiException(HttpStatus.CONFLICT, CLOSED);
        }
        application.linkApproval(approvalRequests.getReferenceById(started.approvalRequestId()));
        applications.saveAndFlush(application);
        return queries.studentApplication(accountId, application.getId());
    }

    /**
     * Cancels the student's own pending application through the approval engine. An already cancelled one is returned
     * as it is; an approved or rejected one, or one with an active allocation, cannot be cancelled (409).
     */
    public HostelApplicationDetail cancel(Long accountId, Long applicationId) {
        HostelApplication application = (applicationId == null ? null
                : applications.findByIdForUpdate(applicationId).orElse(null));
        if (application == null || !application.getStudentProfile().getUserAccount().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        ApprovalRequestStatus status = application.approvalStatus();
        if (status == ApprovalRequestStatus.CANCELLED) {
            return queries.studentApplication(accountId, applicationId);
        }
        if (status != ApprovalRequestStatus.PENDING || allocations.existsActiveForApplication(applicationId)) {
            throw new ApiException(HttpStatus.CONFLICT, CANNOT_CANCEL);
        }
        approvals.cancelApproval(application.getApprovalRequest().getId(), accountId);
        return queries.studentApplication(accountId, applicationId);
    }
}

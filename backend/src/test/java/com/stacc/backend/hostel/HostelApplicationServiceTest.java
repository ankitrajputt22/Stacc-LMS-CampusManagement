package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalRequestRepository;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelRequests.CreateHostelApplicationRequest;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class HostelApplicationServiceTest {

    private final HostelApplicationRepository applications = mock(HostelApplicationRepository.class);
    private final HostelAllocationRepository allocations = mock(HostelAllocationRepository.class);
    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final SemesterEnrollmentRepository enrollments = mock(SemesterEnrollmentRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final ApprovalWorkflowService approvals = mock(ApprovalWorkflowService.class);
    private final ApprovalRequestRepository approvalRequests = mock(ApprovalRequestRepository.class);
    private final HostelQueryService queries = mock(HostelQueryService.class);
    private final HostelApplicationService service = new HostelApplicationService(applications, allocations,
            studentProfiles, enrollments, sessions, approvals, approvalRequests, queries,
            Clock.fixed(HostelFixtures.NOW, ZoneOffset.UTC));

    private final UserAccount account = HostelFixtures.account(70, "student1", RoleName.STUDENT);
    private final StudentProfile student = HostelFixtures.student(5, account);
    private final AcademicSession sessionA = HostelFixtures.session(2, "2026-27", 2026);
    private final AcademicSession sessionB = HostelFixtures.session(3, "2027-28", 2027);
    private final ApprovalRequest pending = HostelFixtures.approval(12, account);

    @BeforeEach
    void setUp() {
        when(studentProfiles.findByUserAccountId(70L)).thenReturn(Optional.of(student));
        when(studentProfiles.findByUserAccountIdForUpdate(70L)).thenReturn(Optional.of(student));
        when(sessions.findById(2L)).thenReturn(Optional.of(sessionA));
        when(sessions.findById(3L)).thenReturn(Optional.of(sessionB));
        when(enrollments.existsInSessionWithStatus(5L, 2L, SemesterEnrollmentStatus.ENROLLED)).thenReturn(true);
        when(enrollments.findSessionsOfStudentWithStatus(5L, SemesterEnrollmentStatus.ENROLLED)).thenReturn(List.of(sessionA));
        when(applications.saveAndFlush(any())).thenAnswer(call -> {
            HostelApplication saved = call.getArgument(0);
            return saved.getId() == null ? HostelFixtures.withId(saved, 42) : saved;
        });
        when(approvals.startApproval(any())).thenReturn(new ApprovalStartResult(12L, ApprovalRequestStatus.PENDING));
        when(approvalRequests.getReferenceById(12L)).thenReturn(pending);
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void eligibleSessionsAreThoseWithAnEnrolledSemesterMarkedWhenAlreadyApplied() {
        when(applications.findSessionIdsWithCurrentApplication(5L)).thenReturn(List.of(2L));

        assertThat(service.eligibleSessions(70L)).singleElement().satisfies(session -> {
            assertThat(session.academicSessionCode()).isEqualTo("2026-27");
            assertThat(session.hasCurrentApplication()).isTrue();
        });
    }

    @Test
    void anInactiveOrMissingProfileHasNoEligibleSessionsAndCannotApply() {
        student.deactivate();
        assertThat(service.eligibleSessions(70L)).isEmpty();
        assertRefused(() -> service.apply(70L, new CreateHostelApplicationRequest(2L, null)), HttpStatus.FORBIDDEN,
                HostelApplicationService.NO_PROFILE);
        assertThat(service.eligibleSessions(99L)).isEmpty();
        assertRefused(() -> service.apply(99L, new CreateHostelApplicationRequest(2L, null)), HttpStatus.FORBIDDEN,
                HostelApplicationService.NO_PROFILE);
        verify(approvals, never()).startApproval(any());
    }

    @Test
    void applyingStartsTheHostelWorkflowAndLinksItsRequest() {
        service.apply(70L, new CreateHostelApplicationRequest(2L, "  Near the library, please. "));

        ArgumentCaptor<HostelApplication> saved = ArgumentCaptor.forClass(HostelApplication.class);
        verify(applications, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        HostelApplication application = saved.getValue();
        assertThat(application.getApplicationNumber()).isEqualTo("HST-000042");
        assertThat(application.getStudentProfile()).isSameAs(student);
        assertThat(application.getNote()).isEqualTo("Near the library, please.");
        assertThat(application.getApprovalRequest()).isSameAs(pending);
        assertThat(application.approvalStatus()).isEqualTo(ApprovalRequestStatus.PENDING);

        ArgumentCaptor<StartApprovalCommand> command = ArgumentCaptor.forClass(StartApprovalCommand.class);
        verify(approvals).startApproval(command.capture());
        assertThat(command.getValue()).isEqualTo(new StartApprovalCommand("HOSTEL_APPLICATION", 70L, "HOSTEL_APPLICATION",
                "42", "Hostel application HST-000042 for 2026-27"));
        verify(studentProfiles).findByUserAccountIdForUpdate(70L);
        verify(queries).studentApplication(70L, 42L);
    }

    @Test
    void onlyEnrolledSessionsWithoutACurrentApplicationCanBeAppliedFor() {
        assertRefused(() -> service.apply(70L, new CreateHostelApplicationRequest(3L, null)), HttpStatus.CONFLICT,
                HostelApplicationService.NOT_ENROLLED);
        assertRefused(() -> service.apply(70L, new CreateHostelApplicationRequest(404L, null)), HttpStatus.NOT_FOUND,
                "Academic session not found.");
        when(applications.existsCurrent(5L, 2L)).thenReturn(true);
        assertRefused(() -> service.apply(70L, new CreateHostelApplicationRequest(2L, null)), HttpStatus.CONFLICT,
                HostelApplicationService.DUPLICATE);
        verify(approvals, never()).startApproval(any());
    }

    @Test
    void reapplyingAfterARejectedOrCancelledApplicationWorks() {
        // existsCurrent counts only PENDING and APPROVED applications, so earlier rejected or cancelled ones do not block.
        when(applications.existsCurrent(5L, 2L)).thenReturn(false);
        service.apply(70L, new CreateHostelApplicationRequest(2L, null));
        verify(approvals).startApproval(any());
    }

    @Test
    void aMissingOrInactiveWorkflowMeansApplicationsAreClosed() {
        when(approvals.startApproval(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "inactive"));
        assertRefused(() -> service.apply(70L, new CreateHostelApplicationRequest(2L, null)), HttpStatus.CONFLICT,
                HostelApplicationService.CLOSED);
    }

    // Cancelling

    private HostelApplication stored(ApprovalRequest approval) {
        HostelApplication application = HostelFixtures.application(42, student, sessionA, approval);
        when(applications.findByIdForUpdate(42L)).thenReturn(Optional.of(application));
        return application;
    }

    @Test
    void aPendingApplicationIsCancelledThroughTheApprovalEngine() {
        stored(pending);

        service.cancel(70L, 42L);

        verify(approvals).cancelApproval(12L, 70L);
    }

    @Test
    void approvedRejectedOrAllocatedApplicationsCannotBeCancelled() {
        UserAccount admin = HostelFixtures.account(1, "admin1", RoleName.ADMIN);
        stored(HostelFixtures.approved(13, account, admin));
        assertRefused(() -> service.cancel(70L, 42L), HttpStatus.CONFLICT, HostelApplicationService.CANNOT_CANCEL);

        ApprovalRequest rejected = HostelFixtures.approval(14, account);
        rejected.reject(rejected.currentStep().orElseThrow(), admin, "No.", HostelFixtures.NOW);
        stored(rejected);
        assertRefused(() -> service.cancel(70L, 42L), HttpStatus.CONFLICT, HostelApplicationService.CANNOT_CANCEL);

        stored(HostelFixtures.approval(15, account));
        when(allocations.existsActiveForApplication(42L)).thenReturn(true);
        assertRefused(() -> service.cancel(70L, 42L), HttpStatus.CONFLICT, HostelApplicationService.CANNOT_CANCEL);
        verify(approvals, never()).cancelApproval(anyLong(), any());
    }

    @Test
    void cancellingAgainChangesNothing() {
        ApprovalRequest cancelled = HostelFixtures.approval(16, account);
        cancelled.cancel(HostelFixtures.NOW);
        stored(cancelled);

        service.cancel(70L, 42L);

        verify(approvals, never()).cancelApproval(anyLong(), any());
        verify(queries).studentApplication(70L, 42L);
    }

    @Test
    void anotherStudentsApplicationLooksMissing() {
        stored(pending);
        assertRefused(() -> service.cancel(71L, 42L), HttpStatus.NOT_FOUND, HostelApplicationService.NOT_FOUND);
        assertRefused(() -> service.cancel(70L, 404L), HttpStatus.NOT_FOUND, HostelApplicationService.NOT_FOUND);
        verify(approvals, never()).cancelApproval(eq(12L), any());
    }
}

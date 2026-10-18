package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentCreatedResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CourseEnrollmentWorkflowTest {

    private final AcademicEnrollmentManagementService enrollments = mock(AcademicEnrollmentManagementService.class);
    private final CourseEnrollmentLmsPort lms = mock(CourseEnrollmentLmsPort.class);
    private final CourseEnrollmentWorkflow workflow = new CourseEnrollmentWorkflow(enrollments, lms);
    private final CourseEnrollmentCreate request = new CourseEnrollmentCreate(31L);

    private static CourseEnrollmentResponse enrollment(String status) {
        return new CourseEnrollmentResponse(140L, 88L, 31L, 12L, "CS301", "Data Structures", new BigDecimal("4.00"), status);
    }

    @Test
    void aNewEnrollmentIsProvisionedOnlyAfterTheOfficialRecordIsSaved() {
        CourseEnrollmentResponse saved = enrollment("ENROLLED");
        when(enrollments.createCourseEnrollment(88L, request)).thenReturn(saved);
        when(lms.provision(140L)).thenReturn(LmsProvisioningOutcome.PROVISIONED);

        CourseEnrollmentCreatedResponse created = workflow.create(88L, request);

        assertSame(saved, created.courseEnrollment());
        assertEquals("PROVISIONED", created.lmsProvisioning());
        var order = org.mockito.Mockito.inOrder(enrollments, lms);
        order.verify(enrollments).createCourseEnrollment(88L, request);
        order.verify(lms).provision(140L);
    }

    @Test
    void anOfferingWithoutAnLmsCourseStillGetsTheOfficialEnrollment() {
        when(enrollments.createCourseEnrollment(88L, request)).thenReturn(enrollment("ENROLLED"));
        when(lms.provision(140L)).thenReturn(LmsProvisioningOutcome.NO_LMS_COURSE);

        CourseEnrollmentCreatedResponse created = workflow.create(88L, request);

        assertEquals("ENROLLED", created.courseEnrollment().status());
        assertEquals("NO_LMS_COURSE", created.lmsProvisioning());
    }

    @Test
    void anLmsFailureIsReportedAndNeverUndoesTheEnrollment() {
        when(enrollments.createCourseEnrollment(88L, request)).thenReturn(enrollment("ENROLLED"));
        when(lms.provision(140L)).thenThrow(new IllegalStateException("membership store unavailable"));

        CourseEnrollmentCreatedResponse created = workflow.create(88L, request);

        assertEquals(140L, created.courseEnrollment().courseEnrollmentId());
        assertEquals("FAILED", created.lmsProvisioning());
        verify(enrollments).createCourseEnrollment(88L, request);
        verifyNoMoreInteractions(enrollments);
    }

    @Test
    void anAcademicRefusalStopsBeforeTheLms() {
        when(enrollments.createCourseEnrollment(88L, request))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.OTHER_SEMESTER));

        assertThrows(ApiException.class, () -> workflow.create(88L, request));
        verifyNoInteractions(lms);
    }

    @Test
    void anEndedEnrollmentsMembershipIsMadeInactiveAndAFailureThereIsOnlyLogged() {
        CourseEnrollmentStatusChange withdraw = new CourseEnrollmentStatusChange(CourseEnrollmentStatus.WITHDRAWN);
        when(enrollments.changeCourseEnrollmentStatus(140L, withdraw)).thenReturn(enrollment("WITHDRAWN"));

        assertEquals("WITHDRAWN", workflow.changeStatus(140L, withdraw).status());
        verify(lms).deactivate(140L);

        doThrow(new IllegalStateException("membership store unavailable")).when(lms).deactivate(140L);
        assertEquals("WITHDRAWN", workflow.changeStatus(140L, withdraw).status());
    }

    @Test
    void anUnchangedEnrolledEnrollmentLeavesTheLmsAlone() {
        CourseEnrollmentStatusChange enrolled = new CourseEnrollmentStatusChange(CourseEnrollmentStatus.ENROLLED);
        when(enrollments.changeCourseEnrollmentStatus(140L, enrolled)).thenReturn(enrollment("ENROLLED"));

        workflow.changeStatus(140L, enrolled);

        verifyNoInteractions(lms);
    }

    @Test
    void aRetryProvisionsAnEnrolledEnrollmentWhoseOfferingHasAnLmsCourse() {
        when(enrollments.requireEnrolledCourseEnrollment(140L)).thenReturn(enrollment("ENROLLED"));
        when(lms.provision(140L)).thenReturn(LmsProvisioningOutcome.PROVISIONED);

        assertEquals("PROVISIONED", workflow.retryProvisioning(140L).lmsProvisioning());
    }

    @Test
    void aRetryIsRefusedWithoutAnLmsCourseOrAnEnrolledEnrollmentAndAFailureAnswers503() {
        when(enrollments.requireEnrolledCourseEnrollment(140L)).thenReturn(enrollment("ENROLLED"));
        when(lms.provision(140L)).thenReturn(LmsProvisioningOutcome.NO_LMS_COURSE);
        ApiException noCourse = assertThrows(ApiException.class, () -> workflow.retryProvisioning(140L));
        assertEquals(HttpStatus.CONFLICT, noCourse.getStatus());
        assertEquals(CourseEnrollmentWorkflow.NO_LMS_COURSE, noCourse.getMessage());

        when(lms.provision(140L)).thenThrow(new IllegalStateException("membership store unavailable"));
        ApiException failed = assertThrows(ApiException.class, () -> workflow.retryProvisioning(140L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failed.getStatus());
        assertEquals(CourseEnrollmentWorkflow.PROVISIONING_FAILED, failed.getMessage());

        when(enrollments.requireEnrolledCourseEnrollment(141L))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, CourseEnrollmentWorkflow.NOT_ENROLLED));
        assertThrows(ApiException.class, () -> workflow.retryProvisioning(141L));
        verify(lms, never()).provision(141L);
        verify(lms, never()).deactivate(any());
    }
}

package com.stacc.backend.academic.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentCreatedResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentLookupResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the admin course-offering and enrollment routes through Spring Security. The services are stand-ins, so these
 * tests are about who gets in, what reaches the services, and what is sent back.
 */
class AdminOfferingEnrollmentControllerTest extends DatabaseFreeApiTest {

    private static final String OFFERINGS = "/api/admin/academic/offerings";
    private static final String ENROLLMENTS = "/api/admin/academic/enrollments";

    @MockitoBean
    private CourseOfferingManagementService offerings;

    @MockitoBean
    private AdminEnrollmentQueryService queries;

    @MockitoBean
    private AcademicEnrollmentManagementService enrollments;

    @MockitoBean
    private CourseEnrollmentWorkflow courseEnrollments;

    private static String token(String... authorities) {
        return TestTokens.valid("ADM-TEST-1", 1, authorities);
    }

    private static String offeringManager() {
        return token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE");
    }

    private static String enrollmentManager() {
        return token("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_VIEW", "ACADEMIC_ENROLLMENT_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> offeringReads() {
        return List.of(get(OFFERINGS), get(OFFERINGS + "/31"));
    }

    private static List<MockHttpServletRequestBuilder> offeringChanges() {
        return List.of(json(post(OFFERINGS), "{\"courseId\":12,\"semesterId\":9}"),
                json(put(OFFERINGS + "/31"), "{\"status\":\"ACTIVE\"}"));
    }

    private static List<MockHttpServletRequestBuilder> enrollmentReads() {
        return List.of(get(OFFERINGS + "/31/enrollments"), get(ENROLLMENTS + "/students").param("q", "STU"),
                get(ENROLLMENTS + "/students/57/semesters"), get(ENROLLMENTS + "/semester-enrollments/88/courses"));
    }

    private static List<MockHttpServletRequestBuilder> enrollmentChanges() {
        return List.of(json(post(ENROLLMENTS + "/students/57/semesters"), "{\"semesterId\":9}"),
                json(put(ENROLLMENTS + "/semester-enrollments/88/status"), "{\"status\":\"COMPLETED\"}"),
                json(post(ENROLLMENTS + "/semester-enrollments/88/courses"), "{\"courseOfferingId\":31}"),
                json(put(ENROLLMENTS + "/course-enrollments/140/status"), "{\"status\":\"WITHDRAWN\"}"),
                post(ENROLLMENTS + "/course-enrollments/140/provision-lms"));
    }

    // Each call gets new requests, so a token is never added to a request that already carries one.
    private void refused(List<String> tokens, Supplier<List<MockHttpServletRequestBuilder>> requests) throws Exception {
        for (String denied : tokens) {
            for (MockHttpServletRequestBuilder request : requests.get()) {
                mockMvc.perform(as(denied, request)).andExpect(status().isForbidden());
            }
        }
        for (MockHttpServletRequestBuilder request : requests.get()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(offerings, queries, enrollments, courseEnrollments);
    }

    @Test
    void readingOfferingsNeedsTheAdminRoleAndTheOfferingViewPermissionTogether() throws Exception {
        refused(List.of(
                token("ROLE_ADMIN", "ACADEMIC_OFFERING_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE", "ACADEMIC_ENROLLMENT_VIEW"),
                token("ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_OFFERING_VIEW", "LMS_COURSE_VIEW"),
                token("ROLE_FACULTY", "ACADEMIC_OFFERING_VIEW", "FACULTY_COURSE_VIEW")), AdminOfferingEnrollmentControllerTest::offeringReads);

        for (MockHttpServletRequestBuilder read : offeringReads()) {
            mockMvc.perform(as(token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW"), read)).andExpect(status().isOk());
        }
    }

    @Test
    void changingOfferingsNeedsTheManagePermissionToo() throws Exception {
        refused(List.of(
                token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW"),
                token("ROLE_ADMIN", "ACADEMIC_OFFERING_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE", "ACADEMIC_ENROLLMENT_VIEW", "ACADEMIC_ENROLLMENT_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE"),
                token("ROLE_FACULTY", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE")), AdminOfferingEnrollmentControllerTest::offeringChanges);
    }

    @Test
    void readingEnrollmentsAndRostersNeedsTheAdminRoleAndTheEnrollmentViewPermission() throws Exception {
        refused(List.of(
                token("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE", "ACADEMIC_MASTER_VIEW"),
                token("ACADEMIC_ENROLLMENT_VIEW"),
                token("ROLE_STUDENT", "ACADEMIC_ENROLLMENT_VIEW", "LMS_COURSE_VIEW"),
                token("ROLE_FACULTY", "ACADEMIC_ENROLLMENT_VIEW", "FACULTY_COURSE_VIEW")), AdminOfferingEnrollmentControllerTest::enrollmentReads);

        for (MockHttpServletRequestBuilder read : enrollmentReads()) {
            mockMvc.perform(as(token("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_VIEW"), read)).andExpect(status().isOk());
        }
    }

    @Test
    void changingEnrollmentsNeedsTheManagePermissionToo() throws Exception {
        refused(List.of(
                token("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_VIEW"),
                token("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_ENROLLMENT_VIEW", "ACADEMIC_ENROLLMENT_MANAGE"),
                token("ROLE_FACULTY", "ACADEMIC_ENROLLMENT_VIEW", "ACADEMIC_ENROLLMENT_MANAGE")), AdminOfferingEnrollmentControllerTest::enrollmentChanges);
    }

    @Test
    void aNonAdminIsRefusedBeforeTheRequestBodyIsValidated() throws Exception {
        String everything = "ACADEMIC_OFFERING_VIEW ACADEMIC_OFFERING_MANAGE ACADEMIC_ENROLLMENT_VIEW ACADEMIC_ENROLLMENT_MANAGE";
        for (String role : new String[] {"ROLE_STUDENT", "ROLE_FACULTY"}) {
            String denied = token((role + " " + everything).split(" "));
            mockMvc.perform(as(denied, json(post(OFFERINGS), "{}"))).andExpect(status().isForbidden());
            mockMvc.perform(as(denied, json(post(ENROLLMENTS + "/students/57/semesters"), "not json"))).andExpect(status().isForbidden());
            mockMvc.perform(as(denied, json(put(ENROLLMENTS + "/course-enrollments/abc/status"), "{}"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(offerings, queries, enrollments, courseEnrollments);
    }

    @Test
    void offeringRequestsReachTheServiceWithThePathAndOnlyTheRequestFields() throws Exception {
        mockMvc.perform(as(offeringManager(), json(post(OFFERINGS), "{\"courseId\":12,\"semesterId\":9,\"status\":\"CLOSED\"}")))
                .andExpect(status().isCreated());
        mockMvc.perform(as(offeringManager(), json(put(OFFERINGS + "/31"), "{\"courseOfferingId\":7,\"status\":\"ACTIVE\",\"semesterId\":10}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(offeringManager(), get(OFFERINGS).param("semesterId", "9").param("academicSessionId", "2")))
                .andExpect(status().isOk());

        verify(offerings).create(new CourseOfferingCreate(12L, 9L));
        verify(offerings).update(31L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, null, 10L));
        verify(offerings).list(9L, null, 2L, null);
    }

    @Test
    void enrollmentRequestsTakeTheStudentAndEnrollmentFromThePathAndNeverAStatusOnCreation() throws Exception {
        when(courseEnrollments.create(eq(88L), any())).thenReturn(new CourseEnrollmentCreatedResponse(
                new CourseEnrollmentResponse(140L, 88L, 31L, 12L, "CS301", "Data Structures", new BigDecimal("4.00"), "ENROLLED"),
                "NO_LMS_COURSE"));

        mockMvc.perform(as(enrollmentManager(), json(post(ENROLLMENTS + "/students/57/semesters"),
                        "{\"semesterId\":9,\"studentProfileId\":58,\"status\":\"COMPLETED\"}")))
                .andExpect(status().isCreated());
        mockMvc.perform(as(enrollmentManager(), json(post(ENROLLMENTS + "/semester-enrollments/88/courses"),
                        "{\"courseOfferingId\":31,\"semesterEnrollmentId\":90,\"status\":\"WITHDRAWN\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseEnrollment.status").value("ENROLLED"))
                .andExpect(jsonPath("$.lmsProvisioning").value("NO_LMS_COURSE"));
        mockMvc.perform(as(enrollmentManager(), json(put(ENROLLMENTS + "/semester-enrollments/88/status"), "{\"status\":\"WITHDRAWN\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(enrollmentManager(), json(put(ENROLLMENTS + "/course-enrollments/140/status"), "{\"status\":\"COMPLETED\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(enrollmentManager(), post(ENROLLMENTS + "/course-enrollments/140/provision-lms")))
                .andExpect(status().isOk());

        verify(enrollments).createSemesterEnrollment(57L, new SemesterEnrollmentCreate(9L));
        verify(courseEnrollments).create(88L, new CourseEnrollmentCreate(31L));
        verify(enrollments).changeSemesterEnrollmentStatus(88L, new SemesterEnrollmentStatusChange(SemesterEnrollmentStatus.WITHDRAWN));
        verify(courseEnrollments).changeStatus(140L, new CourseEnrollmentStatusChange(CourseEnrollmentStatus.COMPLETED));
        verify(courseEnrollments).retryProvisioning(140L);
    }

    @Test
    void theStudentLookupSendsOnlyAcademicIdentifiers() throws Exception {
        when(queries.searchStudents("STU")).thenReturn(List.of(
                new StudentLookupResponse(57L, "STU-TEST-001", 4L, "BTECH-CSE", "B.Tech. CSE", "2026-27", "ACTIVE")));

        mockMvc.perform(as(enrollmentManager(), get(ENROLLMENTS + "/students").param("q", "STU")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentProfileId").value(57))
                .andExpect(jsonPath("$[0].loginId").value("STU-TEST-001"))
                .andExpect(jsonPath("$[0].programCode").value("BTECH-CSE"))
                .andExpect(jsonPath("$[0].admissionSessionCode").value("2026-27"))
                .andExpect(jsonPath("$[0].studentProfileStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].roles").doesNotExist())
                .andExpect(jsonPath("$[0].permissions").doesNotExist())
                .andExpect(jsonPath("$[0].userAccountId").doesNotExist());
    }

    @Test
    void invalidRequestsAreRefusedBeforeTheServices() throws Exception {
        mockMvc.perform(as(offeringManager(), json(post(OFFERINGS), "{\"courseId\":12}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(offeringManager(), json(put(OFFERINGS + "/31"), "{\"status\":\"ARCHIVED\"}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(offeringManager(), json(put(OFFERINGS + "/31"), "{\"courseId\":12}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(offeringManager(), get(OFFERINGS).param("semesterId", "third"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(enrollmentManager(), json(post(ENROLLMENTS + "/students/57/semesters"), "{}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(enrollmentManager(), json(post(ENROLLMENTS + "/semester-enrollments/88/courses"), "{}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(enrollmentManager(), json(put(ENROLLMENTS + "/semester-enrollments/88/status"), "{\"status\":\"PAUSED\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(enrollmentManager(), json(put(ENROLLMENTS + "/course-enrollments/abc/status"), "{\"status\":\"COMPLETED\"}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offerings, queries, enrollments, courseEnrollments);
    }

    @Test
    void refusalsFromTheServicesKeepTheirStatusAndSafeMessage() throws Exception {
        when(offerings.create(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, CourseOfferingManagementService.TAKEN));
        when(enrollments.createSemesterEnrollment(eq(57L), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.SEMESTER_TAKEN));
        when(courseEnrollments.create(eq(88L), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.OTHER_SEMESTER));
        when(enrollments.changeSemesterEnrollmentStatus(eq(88L), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.ACTIVE_COURSES));
        when(queries.studentSemesterEnrollments(99L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, AdminEnrollmentQueryService.STUDENT_NOT_FOUND));
        when(courseEnrollments.retryProvisioning(140L))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, CourseEnrollmentWorkflow.PROVISIONING_FAILED));

        mockMvc.perform(as(offeringManager(), json(post(OFFERINGS), "{\"courseId\":12,\"semesterId\":9}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This course offering already exists."));
        mockMvc.perform(as(enrollmentManager(), json(post(ENROLLMENTS + "/students/57/semesters"), "{\"semesterId\":9}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This student is already enrolled in this semester."));
        mockMvc.perform(as(enrollmentManager(), json(post(ENROLLMENTS + "/semester-enrollments/88/courses"), "{\"courseOfferingId\":33}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AcademicEnrollmentManagementService.OTHER_SEMESTER));
        mockMvc.perform(as(enrollmentManager(), json(put(ENROLLMENTS + "/semester-enrollments/88/status"), "{\"status\":\"COMPLETED\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Active course enrollments must be resolved before closing the semester enrollment."));
        mockMvc.perform(as(enrollmentManager(), get(ENROLLMENTS + "/students/99/semesters")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Student not found."));
        mockMvc.perform(as(enrollmentManager(), post(ENROLLMENTS + "/course-enrollments/140/provision-lms")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(CourseEnrollmentWorkflow.PROVISIONING_FAILED));
    }

    @Test
    void thereIsNoDeleteForOfferingsOrEnrollments() throws Exception {
        String everything = token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE", "ACADEMIC_ENROLLMENT_VIEW",
                "ACADEMIC_ENROLLMENT_MANAGE");
        for (String path : new String[] {OFFERINGS + "/31", ENROLLMENTS + "/students/57/semesters",
                ENROLLMENTS + "/semester-enrollments/88/status", ENROLLMENTS + "/semester-enrollments/88/courses",
                ENROLLMENTS + "/course-enrollments/140/status"}) {
            mockMvc.perform(as(everything, delete(path))).andExpect(status().isMethodNotAllowed());
        }
        verifyNoInteractions(offerings, queries, enrollments, courseEnrollments);
    }

    @Test
    void everyRouteIsDocumentedWithTheBearerSchemeAndItsRefusals() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        String[][] operations = {
                {OFFERINGS, "get"}, {OFFERINGS, "post"}, {OFFERINGS + "/{courseOfferingId}", "get"},
                {OFFERINGS + "/{courseOfferingId}", "put"}, {OFFERINGS + "/{courseOfferingId}/enrollments", "get"},
                {ENROLLMENTS + "/students", "get"}, {ENROLLMENTS + "/students/{studentProfileId}/semesters", "get"},
                {ENROLLMENTS + "/students/{studentProfileId}/semesters", "post"},
                {ENROLLMENTS + "/semester-enrollments/{semesterEnrollmentId}/status", "put"},
                {ENROLLMENTS + "/semester-enrollments/{semesterEnrollmentId}/courses", "get"},
                {ENROLLMENTS + "/semester-enrollments/{semesterEnrollmentId}/courses", "post"},
                {ENROLLMENTS + "/course-enrollments/{courseEnrollmentId}/status", "put"},
                {ENROLLMENTS + "/course-enrollments/{courseEnrollmentId}/provision-lms", "post"}};
        for (String[] operation : operations) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists());
        }
        for (String created : new String[] {OFFERINGS, ENROLLMENTS + "/students/{studentProfileId}/semesters",
                ENROLLMENTS + "/semester-enrollments/{semesterEnrollmentId}/courses"}) {
            String path = "$.paths['" + created + "'].post";
            result.andExpect(jsonPath(path + ".responses['201']").exists())
                    .andExpect(jsonPath(path + ".responses['400']").exists())
                    .andExpect(jsonPath(path + ".responses['404']").exists())
                    .andExpect(jsonPath(path + ".responses['409']").exists());
        }
        result.andExpect(jsonPath("$.paths['" + OFFERINGS + "/{courseOfferingId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + OFFERINGS + "/{courseOfferingId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SemesterEnrollmentCreate.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SemesterEnrollmentCreate.properties.studentProfileId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CourseEnrollmentCreate.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CourseEnrollmentCreate.properties.semesterEnrollmentId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StudentLookupResponse.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StudentLookupResponse.properties.roles").doesNotExist());
    }
}

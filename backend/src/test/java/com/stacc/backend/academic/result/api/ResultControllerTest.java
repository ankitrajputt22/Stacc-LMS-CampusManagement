package com.stacc.backend.academic.result.api;

import static org.mockito.ArgumentMatchers.anyLong;
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
import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultManagementService;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Calls the result routes through Spring Security. The services are stand-ins, so these tests are about who gets
 * in, what reaches the services, and what is sent back.
 */
class ResultControllerTest extends DatabaseFreeApiTest {

    private static final long FACULTY_ACCOUNT = 21;
    private static final long STUDENT_ACCOUNT = 11;
    private static final String SAVE_PATH = "/api/results/faculty/course-offerings/17/enrollments/40";
    private static final String RESULT_BODY = "{\"marksObtained\":82,\"maxMarks\":100,\"gradeCode\":\"A\",\"remarks\":\"Good performance\"}";

    @MockitoBean
    private FacultyResultQueryService facultyQueries;

    @MockitoBean
    private StudentResultQueryService studentQueries;

    @MockitoBean
    private CourseResultManagementService managementService;

    private static String faculty(String... authorities) {
        return TestTokens.valid("EMP-TEST-21", FACULTY_ACCOUNT, authorities);
    }

    private static String student(String... authorities) {
        return TestTokens.valid("2408400100011", STUDENT_ACCOUNT, authorities);
    }

    private static String manager() {
        return faculty("ROLE_FACULTY", "ACADEMIC_RESULT_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> facultyRoutes() {
        return List.of(
                get("/api/results/faculty/my-courses"),
                get("/api/results/faculty/course-offerings/17"),
                json(put(SAVE_PATH), RESULT_BODY),
                post(SAVE_PATH + "/publish"));
    }

    private static CourseResult draft() {
        CourseOffering offering = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
        CourseResult result = new CourseResult(AttendanceFixtures.enrollment(offering, STUDENT_ACCOUNT, 40, "2408400100011"),
                AttendanceFixtures.faculty(FACULTY_ACCOUNT, 61), new BigDecimal("82"), new BigDecimal("100"), "A", "Good performance");
        ReflectionTestUtils.setField(result, "id", 300L);
        return result;
    }

    @Test
    void facultyWithTheResultPermissionGetTheirCourseOfferingsWithoutTheLmsPermission() throws Exception {
        when(facultyQueries.getMyCourseOfferings(FACULTY_ACCOUNT)).thenReturn(List.of(
                new ResultCourseOfferingResponse(17L, "BCS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27")));

        mockMvc.perform(as(manager(), get("/api/results/faculty/my-courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseOfferingId").value(17))
                .andExpect(jsonPath("$[0].lmsCourseId").doesNotExist());
    }

    @Test
    void everyFacultyRouteNeedsTheFacultyRoleAndTheResultPermissionTogether() throws Exception {
        for (String token : new String[] {
                faculty("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE", "ATTENDANCE_MANAGE"),
                faculty("ACADEMIC_RESULT_MANAGE"),
                student("ROLE_STUDENT", "ACADEMIC_RESULT_VIEW", "ACADEMIC_RESULT_MANAGE"),
                faculty("ROLE_ADMIN", "ACADEMIC_RESULT_MANAGE", "ACADEMIC_RESULT_VIEW")}) {
            for (MockHttpServletRequestBuilder route : facultyRoutes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        for (MockHttpServletRequestBuilder route : facultyRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(facultyQueries, managementService);
    }

    @Test
    void savingSendsTheRouteIdsAndOnlyTheFourFields() throws Exception {
        when(managementService.save(eq(FACULTY_ACCOUNT), eq(17L), eq(40L), eq(new BigDecimal("82")), eq(new BigDecimal("100")),
                eq("A"), eq("Good performance"))).thenReturn(draft());

        mockMvc.perform(as(manager(), json(put(SAVE_PATH),
                        "{\"marksObtained\":82,\"maxMarks\":100,\"gradeCode\":\"A\",\"remarks\":\"Good performance\","
                                + "\"status\":\"PUBLISHED\",\"publishedAt\":\"2026-01-01T00:00:00Z\",\"recordedByFacultyProfileId\":99}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultId").value(300))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.marksObtained").value(82.0))
                .andExpect(jsonPath("$.recordedByFacultyProfileId").doesNotExist());

        verify(managementService).save(FACULTY_ACCOUNT, 17L, 40L, new BigDecimal("82"), new BigDecimal("100"), "A", "Good performance");
    }

    @Test
    void invalidResultsAreRefusedBeforeTheService() throws Exception {
        for (String body : new String[] {
                "{\"maxMarks\":100}",
                "{\"marksObtained\":82}",
                "{\"marksObtained\":-1,\"maxMarks\":100}",
                "{\"marksObtained\":0,\"maxMarks\":0}",
                "{\"marksObtained\":1.555,\"maxMarks\":100}",
                "{\"marksObtained\":1,\"maxMarks\":1000000}",
                "{\"marksObtained\":1,\"maxMarks\":100,\"gradeCode\":\"" + "x".repeat(21) + "\"}",
                "{\"marksObtained\":1,\"maxMarks\":100,\"remarks\":\"" + "x".repeat(2001) + "\"}",
                "{\"marksObtained\":\"lots\",\"maxMarks\":100}"}) {
            mockMvc.perform(as(manager(), json(put(SAVE_PATH), body))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void refusalsFromTheServiceKeepTheirStatusAndMessage() throws Exception {
        when(managementService.publish(FACULTY_ACCOUNT, 17L, 40L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "No result has been saved for this student yet. Save it before publishing."));
        when(managementService.save(eq(FACULTY_ACCOUNT), eq(17L), eq(41L), eq(new BigDecimal("101")), eq(new BigDecimal("100")),
                eq(null), eq(null))).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Marks obtained cannot exceed maximum marks."));

        mockMvc.perform(as(manager(), post(SAVE_PATH + "/publish")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No result has been saved for this student yet. Save it before publishing."));
        mockMvc.perform(as(manager(), json(put("/api/results/faculty/course-offerings/17/enrollments/41"),
                        "{\"marksObtained\":101,\"maxMarks\":100}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Marks obtained cannot exceed maximum marks."));
    }

    @Test
    void publishingAnswersWithThePublishedResult() throws Exception {
        CourseResult published = draft();
        published.publish(Instant.parse("2026-11-20T10:00:00Z"));
        when(managementService.publish(FACULTY_ACCOUNT, 17L, 40L)).thenReturn(published);

        mockMvc.perform(as(manager(), post(SAVE_PATH + "/publish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-11-20T10:00:00Z"));
    }

    @Test
    void thereIsNoDeleteOrUnpublish() throws Exception {
        mockMvc.perform(as(manager(), delete(SAVE_PATH))).andExpect(status().is4xxClientError());
        mockMvc.perform(as(manager(), post(SAVE_PATH + "/unpublish"))).andExpect(status().is4xxClientError());
        verifyNoInteractions(managementService);
    }

    @Test
    void aStudentWithTheViewPermissionGetsTheirOwnResultsFromTheToken() throws Exception {
        when(studentQueries.getMyResults(STUDENT_ACCOUNT)).thenReturn(new StudentResultsResponse(List.of(
                new StudentResultsResponse.Result("BCS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27",
                        new BigDecimal("82.00"), new BigDecimal("100.00"), "A", null, Instant.parse("2026-11-20T10:00:00Z")))));

        mockMvc.perform(as(student("ROLE_STUDENT", "ACADEMIC_RESULT_VIEW"), get("/api/results/student/me?studentProfileId=12&courseEnrollmentId=5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].courseCode").value("BCS301"))
                .andExpect(jsonPath("$.results[0].marksObtained").value(82.0))
                .andExpect(jsonPath("$.results[0].courseEnrollmentId").doesNotExist())
                .andExpect(jsonPath("$.results[0].courseOfferingId").doesNotExist());

        verify(studentQueries).getMyResults(STUDENT_ACCOUNT);
    }

    @Test
    void theStudentRouteNeedsTheStudentRoleAndTheViewPermissionTogether() throws Exception {
        for (String token : new String[] {
                student("ROLE_STUDENT", "LMS_COURSE_VIEW", "ATTENDANCE_VIEW"),
                student("ACADEMIC_RESULT_VIEW"),
                faculty("ROLE_FACULTY", "ACADEMIC_RESULT_MANAGE", "ACADEMIC_RESULT_VIEW"),
                faculty("ROLE_ADMIN", "ACADEMIC_RESULT_MANAGE", "ACADEMIC_RESULT_VIEW")}) {
            mockMvc.perform(as(token, get("/api/results/student/me"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/results/student/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(studentQueries);
    }

    @Test
    void anAccountWithBothRolesUsesEachSideSeparately() throws Exception {
        String both = TestTokens.valid("2408400100015", 15, "ROLE_STUDENT", "ROLE_FACULTY", "ACADEMIC_RESULT_VIEW", "ACADEMIC_RESULT_MANAGE");
        when(studentQueries.getMyResults(anyLong())).thenReturn(new StudentResultsResponse(List.of()));

        mockMvc.perform(as(both, get("/api/results/student/me"))).andExpect(status().isOk());
        mockMvc.perform(as(both, get("/api/results/faculty/my-courses"))).andExpect(status().isOk());

        verify(studentQueries).getMyResults(15L);
        verify(facultyQueries).getMyCourseOfferings(15L);
    }

    @Test
    void theRoutesAreDocumentedWithTheBearerScheme() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : new String[][] {
                {"/api/results/faculty/my-courses", "get"},
                {"/api/results/faculty/course-offerings/{courseOfferingId}", "get"},
                {"/api/results/faculty/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}", "put"},
                {"/api/results/faculty/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}/publish", "post"},
                {"/api/results/student/me", "get"}}) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['200']").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists());
        }
        String save = "$.paths['/api/results/faculty/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}']";
        result.andExpect(jsonPath(save + ".put.responses['400']").exists())
                .andExpect(jsonPath(save + ".put.responses['404']").exists())
                .andExpect(jsonPath(save + ".delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CourseResultRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.FacultyResultResponse.properties.recordedByFacultyProfileId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StudentResultsResponse").exists());
    }
}

package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the assignment routes through Spring Security. The services are stand-ins, so these tests are about
 * who gets in, what is checked at the door, and what is sent back. Deadlines are fixed UTC moments.
 */
class LmsAssignmentControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final Instant DUE = Instant.parse("2026-10-20T18:29:00Z");
    private static final BigDecimal MARKS = new BigDecimal("20");
    private static final String VALID =
            "{\"title\":\"Arrays practice\",\"instructions\":\"Solve 1 to 5.\\nShow working.\","
                    + "\"dueAt\":\"2026-10-20T18:29:00Z\",\"maxMarks\":20}";

    @MockitoBean
    private LmsStudentAssignmentQueryService studentQueries;

    @MockitoBean
    private LmsFacultyAssignmentQueryService facultyQueries;

    @MockitoBean
    private LmsAssignmentManagementService managementService;

    private final LmsAssignment assignment = assignment();

    private static LmsAssignment assignment() {
        LmsAssignment created = new LmsAssignment(ContentFixtures.lmsCourse(7), "Arrays practice", "Solve 1 to 5.\nShow working.", DUE, MARKS);
        ReflectionTestUtils.setField(created, "id", 9L);
        return created;
    }

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static String studentToken() {
        return tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW");
    }

    private static String facultyReaderToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW");
    }

    private static String managerToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> studentRoutes() {
        return List.of(get("/api/lms/courses/7/assignments"), get("/api/lms/courses/7/assignments/9"));
    }

    private static List<MockHttpServletRequestBuilder> facultyReadRoutes() {
        return List.of(get("/api/lms/faculty/courses/7/assignments"), get("/api/lms/faculty/my-assignments"));
    }

    private static List<MockHttpServletRequestBuilder> changeRoutes() {
        return List.of(
                json(post("/api/lms/faculty/courses/7/assignments"), VALID),
                json(put("/api/lms/faculty/assignments/9"), VALID),
                post("/api/lms/faculty/assignments/9/publish"),
                post("/api/lms/faculty/assignments/9/archive"));
    }

    @Test
    void studentGetsPublishedAssignmentsWithoutStatus() throws Exception {
        when(studentQueries.getPublishedAssignments(ACCOUNT_ID, 7L)).thenReturn(List.of(
                new LmsCourseAssignmentResponse(9L, "Arrays practice", DUE, new BigDecimal("20.00"))));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/assignments").param("accountId", "999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(4)))
                .andExpect(jsonPath("$[0].dueAt").value("2026-10-20T18:29:00Z"))
                .andExpect(content().string(containsString("\"maxMarks\":20.00")))
                .andExpect(content().string(not(containsString("status"))));

        verify(studentQueries).getPublishedAssignments(ACCOUNT_ID, 7L);
    }

    @Test
    void studentOpensOnePublishedAssignmentWithItsCourse() throws Exception {
        when(studentQueries.getPublishedAssignment(ACCOUNT_ID, 7L, 9L)).thenReturn(new LmsCourseAssignmentDetailResponse(
                9L, 7L, "TST301", "Test Data Structures", "Arrays practice", "Solve 1 to 5.\nShow working.", DUE,
                new BigDecimal("12.50"), Instant.parse("2026-10-14T09:30:00Z")));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/assignments/9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(9)))
                .andExpect(jsonPath("$.courseCode").value("TST301"))
                .andExpect(jsonPath("$.instructions").value("Solve 1 to 5.\nShow working."))
                .andExpect(jsonPath("$.dueAt").value("2026-10-20T18:29:00Z"))
                .andExpect(content().string(containsString("\"maxMarks\":12.50")))
                .andExpect(content().string(not(containsString("status"))));
    }

    @Test
    void studentRoutesNeedTheStudentRoleAndPermission() throws Exception {
        for (MockHttpServletRequestBuilder route : studentRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(tokenWith("ROLE_STUDENT"), facultyReaderToken(), managerToken(),
                tokenWith("ROLE_ADMIN", "LMS_COURSE_VIEW"), tokenWith("LMS_COURSE_VIEW"))) {
            for (MockHttpServletRequestBuilder route : studentRoutes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(studentQueries);
    }

    @Test
    void unavailableCourseOrAssignmentKeepsTheSafeMessage() throws Exception {
        when(studentQueries.getPublishedAssignments(ACCOUNT_ID, 7L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Course not found or unavailable."));
        when(studentQueries.getPublishedAssignment(ACCOUNT_ID, 7L, 9L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Assignment not found or unavailable."));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/assignments")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Course not found or unavailable."));
        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/assignments/9")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Assignment not found or unavailable."));
    }

    @Test
    void facultyReadsWithTheCourseViewPermissionAlone() throws Exception {
        when(facultyQueries.getManagedAssignments(ACCOUNT_ID, 7L)).thenReturn(List.of(LmsFacultyAssignmentResponse.of(assignment)));
        when(facultyQueries.getMyAssignments(ACCOUNT_ID)).thenReturn(List.of(new LmsFacultyAssignmentOverviewResponse(
                7L, "TST301", "Test Data Structures", 9L, "Arrays practice", DUE, new BigDecimal("20.00"), LmsContentStatus.DRAFT)));

        mockMvc.perform(as(facultyReaderToken(), get("/api/lms/faculty/courses/7/assignments")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].publishedAt").doesNotExist())
                .andExpect(jsonPath("$[0].dueAt").value("2026-10-20T18:29:00Z"))
                .andExpect(jsonPath("$[0].*", hasSize(9)));
        mockMvc.perform(as(facultyReaderToken(), get("/api/lms/faculty/my-assignments")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].*", hasSize(8)))
                .andExpect(jsonPath("$[0].courseCode").value("TST301"))
                .andExpect(jsonPath("$[0].assignmentId").value(9));

        verify(facultyQueries).getMyAssignments(ACCOUNT_ID);
    }

    @Test
    void facultyReadRoutesNeedTheFacultyRoleAndViewPermission() throws Exception {
        for (MockHttpServletRequestBuilder route : facultyReadRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(tokenWith("ROLE_FACULTY"), tokenWith("ROLE_FACULTY", "LMS_ASSIGNMENT_MANAGE"), studentToken(),
                tokenWith("ROLE_STUDENT", "FACULTY_COURSE_VIEW"), tokenWith("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : facultyReadRoutes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(facultyQueries);
    }

    @Test
    void addingAnAssignmentAnswersCreatedAndIgnoresStatusAndOwnershipInTheBody() throws Exception {
        when(managementService.create(ACCOUNT_ID, 7L, "Arrays practice", "Solve 1 to 5.\nShow working.", DUE, MARKS))
                .thenReturn(assignment);

        mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/courses/7/assignments"),
                        "{\"title\":\"Arrays practice\",\"instructions\":\"Solve 1 to 5.\\nShow working.\","
                                + "\"dueAt\":\"2026-10-20T18:29:00Z\",\"maxMarks\":20,\"status\":\"PUBLISHED\","
                                + "\"publishedAt\":\"2020-01-01T00:00:00Z\",\"lmsCourseId\":99,\"facultyProfileId\":3,"
                                + "\"courseOfferingId\":4}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());

        verify(managementService).create(ACCOUNT_ID, 7L, "Arrays practice", "Solve 1 to 5.\nShow working.", DUE, MARKS);
    }

    @Test
    void deadlineWithAnOffsetArrivesAsTheSameMomentAndIsSentBackInUtc() throws Exception {
        when(managementService.update(eq(ACCOUNT_ID), eq(9L), any(), any(), any(), any())).thenReturn(assignment);

        mockMvc.perform(as(managerToken(), json(put("/api/lms/faculty/assignments/9"),
                        "{\"title\":\"Arrays practice\",\"instructions\":\"Solve 1 to 5.\\nShow working.\","
                                + "\"dueAt\":\"2026-10-20T23:59:00+05:30\",\"maxMarks\":12.5}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value("2026-10-20T18:29:00Z"));

        verify(managementService).update(ACCOUNT_ID, 9L, "Arrays practice", "Solve 1 to 5.\nShow working.", DUE,
                new BigDecimal("12.5"));
    }

    @Test
    void eachChangeRouteCallsItsActionForTheAccountInTheToken() throws Exception {
        when(managementService.update(ACCOUNT_ID, 9L, "Arrays practice", "Solve 1 to 5.\nShow working.", DUE, MARKS))
                .thenReturn(assignment);
        when(managementService.publish(ACCOUNT_ID, 9L)).thenReturn(assignment);
        when(managementService.archive(ACCOUNT_ID, 9L)).thenReturn(assignment);

        for (MockHttpServletRequestBuilder route : changeRoutes().subList(1, 4)) {
            mockMvc.perform(as(managerToken(), route)).andExpect(status().isOk());
        }

        verify(managementService).update(ACCOUNT_ID, 9L, "Arrays practice", "Solve 1 to 5.\nShow working.", DUE, MARKS);
        verify(managementService).publish(ACCOUNT_ID, 9L);
        verify(managementService).archive(ACCOUNT_ID, 9L);
    }

    @Test
    void changesNeedTheFacultyRoleTheViewPermissionAndTheAssignmentPermission() throws Exception {
        for (MockHttpServletRequestBuilder route : changeRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(
                facultyReaderToken(),
                tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "LMS_ASSIGNMENT_MANAGE"),
                tokenWith("FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"),
                tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"),
                tokenWith("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : changeRoutes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void invalidFieldsAreRefusedWithFieldErrorsBeforeAnyChange() throws Exception {
        String instructions = "\"instructions\":\"Text\"";
        String due = "\"dueAt\":\"2026-10-20T18:29:00Z\"";
        for (String body : List.of(
                "{" + instructions + "," + due + ",\"maxMarks\":20}",
                "{\"title\":\" \"," + instructions + "," + due + ",\"maxMarks\":20}",
                "{\"title\":\"" + "x".repeat(201) + "\"," + instructions + "," + due + ",\"maxMarks\":20}",
                "{\"title\":\"Title\"," + due + ",\"maxMarks\":20}",
                "{\"title\":\"Title\",\"instructions\":\"  \"," + due + ",\"maxMarks\":20}",
                "{\"title\":\"Title\",\"instructions\":\"" + "x".repeat(20_001) + "\"," + due + ",\"maxMarks\":20}",
                "{\"title\":\"Title\"," + instructions + ",\"maxMarks\":20}",
                "{\"title\":\"Title\"," + instructions + "," + due + "}",
                "{\"title\":\"Title\"," + instructions + "," + due + ",\"maxMarks\":0}",
                "{\"title\":\"Title\"," + instructions + "," + due + ",\"maxMarks\":-5}",
                "{\"title\":\"Title\"," + instructions + "," + due + ",\"maxMarks\":12.555}",
                "{\"title\":\"Title\"," + instructions + "," + due + ",\"maxMarks\":1000000}")) {
            mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/courses/7/assignments"), body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
            mockMvc.perform(as(managerToken(), json(put("/api/lms/faculty/assignments/9"), body)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void deadlineOutsideTheSupportedYearsIsAFieldError() throws Exception {
        for (String dueAt : List.of("\"1999-12-31T18:29:00Z\"", "\"+20000-01-01T00:00:00Z\"")) {
            mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/courses/7/assignments"),
                            "{\"title\":\"Title\",\"instructions\":\"Text\",\"dueAt\":" + dueAt + ",\"maxMarks\":20}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("dueAt"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Choose a due date between the years 2000 and 9999"));
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void deadlineWithoutATimeZoneIsRefusedAsUnreadable() throws Exception {
        for (String dueAt : List.of("\"2026-10-20T18:29:00\"", "\"2026-10-20\"", "\"next Friday\"")) {
            mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/courses/7/assignments"),
                            "{\"title\":\"Title\",\"instructions\":\"Text\",\"dueAt\":" + dueAt + ",\"maxMarks\":20}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void refusalsKeepTheirStatusAndSafeMessage() throws Exception {
        when(managementService.publish(anyLong(), eq(6L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Assignment not found or unavailable."));
        when(managementService.publish(anyLong(), eq(9L)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "This assignment is archived and can no longer be changed."));

        mockMvc.perform(as(managerToken(), post("/api/lms/faculty/assignments/6/publish")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Assignment not found or unavailable."));
        mockMvc.perform(as(managerToken(), post("/api/lms/faculty/assignments/9/publish")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This assignment is archived and can no longer be changed."));
    }

    @Test
    void assignmentsCannotBeDeleted() throws Exception {
        mockMvc.perform(as(managerToken(), delete("/api/lms/faculty/assignments/9"))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(managementService);
    }

    @Test
    void assignmentRoutesAppearInTheApiDocumentation() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : new String[][] {
                {"/api/lms/courses/{lmsCourseId}/assignments", "get", "200"},
                {"/api/lms/courses/{lmsCourseId}/assignments/{assignmentId}", "get", "200"},
                {"/api/lms/faculty/courses/{lmsCourseId}/assignments", "get", "200"},
                {"/api/lms/faculty/my-assignments", "get", "200"},
                {"/api/lms/faculty/courses/{lmsCourseId}/assignments", "post", "201"},
                {"/api/lms/faculty/assignments/{assignmentId}", "put", "200"},
                {"/api/lms/faculty/assignments/{assignmentId}/publish", "post", "200"},
                {"/api/lms/faculty/assignments/{assignmentId}/archive", "post", "200"}}) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['" + operation[2] + "']").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists());
        }
        for (String[] operation : new String[][] {
                {"/api/lms/courses/{lmsCourseId}/assignments", "get"},
                {"/api/lms/courses/{lmsCourseId}/assignments/{assignmentId}", "get"},
                {"/api/lms/faculty/courses/{lmsCourseId}/assignments", "post"},
                {"/api/lms/faculty/assignments/{assignmentId}", "put"},
                {"/api/lms/faculty/assignments/{assignmentId}/publish", "post"},
                {"/api/lms/faculty/assignments/{assignmentId}/archive", "post"}}) {
            result.andExpect(jsonPath("$.paths['" + operation[0] + "']." + operation[1] + ".responses['404']").exists());
        }
        result.andExpect(jsonPath("$.paths['/api/lms/faculty/courses/{lmsCourseId}/assignments'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/assignments/{assignmentId}'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/assignments/{assignmentId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsCourseAssignmentResponse.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsCourseAssignmentDetailResponse.properties.status").doesNotExist());
    }
}

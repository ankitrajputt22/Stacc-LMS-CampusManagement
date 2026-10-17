package com.stacc.backend.academic.attendance.api;

import static org.hamcrest.Matchers.hasSize;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.attendance.AttendanceManagementService;
import com.stacc.backend.academic.attendance.AttendanceManagementService.RecordChange;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the attendance routes through Spring Security. The services are stand-ins, so these tests are about who
 * gets in, what reaches the services, and what is sent back.
 */
class AttendanceControllerTest extends DatabaseFreeApiTest {

    private static final long FACULTY_ACCOUNT = 21;
    private static final long STUDENT_ACCOUNT = 11;
    private static final String SESSION_BODY = "{\"heldAt\":\"2026-10-19T04:30:00Z\",\"topic\":\"Arrays introduction\"}";

    @MockitoBean
    private FacultyAttendanceQueryService facultyQueries;

    @MockitoBean
    private StudentAttendanceQueryService studentQueries;

    @MockitoBean
    private AttendanceManagementService managementService;

    private static String faculty(String... authorities) {
        return TestTokens.valid("EMP-TEST-21", FACULTY_ACCOUNT, authorities);
    }

    private static String student(String... authorities) {
        return TestTokens.valid("2408400100011", STUDENT_ACCOUNT, authorities);
    }

    private static String manager() {
        return faculty("ROLE_FACULTY", "ATTENDANCE_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> facultyRoutes() {
        return List.of(
                get("/api/attendance/faculty/my-courses"),
                get("/api/attendance/faculty/course-offerings/17/sessions"),
                json(post("/api/attendance/faculty/course-offerings/17/sessions"), SESSION_BODY),
                get("/api/attendance/faculty/sessions/8"),
                json(put("/api/attendance/faculty/sessions/8"), SESSION_BODY),
                json(put("/api/attendance/faculty/sessions/8/records"), "{\"records\":[]}"),
                post("/api/attendance/faculty/sessions/8/finalize"));
    }

    private static AttendanceSession session() {
        return AttendanceFixtures.session(AttendanceFixtures.offering(17, "BCS301", "Data Structures"),
                AttendanceFixtures.faculty(FACULTY_ACCOUNT, 61), 8, "2026-10-19T04:30:00Z");
    }

    private static AttendanceSessionDetailResponse detail() {
        return new AttendanceSessionDetailResponse(8L, 17L, "BCS301", "Data Structures", Instant.parse("2026-10-19T04:30:00Z"),
                "Arrays introduction", "OPEN", null,
                List.of(new AttendanceRecordResponse(101L, "2408400100011", "UNMARKED", null)));
    }

    @Test
    void facultyWithTheAttendancePermissionGetTheirCourseOfferings() throws Exception {
        when(facultyQueries.getMyCourseOfferings(FACULTY_ACCOUNT)).thenReturn(List.of(new AttendanceCourseOfferingResponse(
                17L, "BCS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27")));

        mockMvc.perform(as(manager(), get("/api/attendance/faculty/my-courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseOfferingId").value(17))
                .andExpect(jsonPath("$[0].courseCode").value("BCS301"))
                .andExpect(jsonPath("$[0].lmsCourseId").doesNotExist())
                .andExpect(jsonPath("$[0].studentCount").doesNotExist());
    }

    @Test
    void everyFacultyRouteNeedsTheFacultyRoleAndTheAttendancePermissionTogether() throws Exception {
        for (String token : new String[] {
                faculty("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE"),
                faculty("ATTENDANCE_MANAGE"),
                student("ROLE_STUDENT", "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE"),
                faculty("ROLE_ADMIN", "ATTENDANCE_MANAGE", "ATTENDANCE_VIEW")}) {
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
    void creatingASessionSendsOnlyTheTimeAndTopic() throws Exception {
        when(managementService.createSession(eq(FACULTY_ACCOUNT), eq(17L), any(), any())).thenReturn(session());

        mockMvc.perform(as(manager(), json(post("/api/attendance/faculty/course-offerings/17/sessions"),
                        "{\"heldAt\":\"2026-10-19T04:30:00Z\",\"topic\":\"Arrays introduction\",\"status\":\"FINALIZED\","
                                + "\"createdByFacultyProfileId\":99,\"courseEnrollmentIds\":[1,2]}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.finalizedAt").doesNotExist())
                .andExpect(jsonPath("$.createdByFacultyProfileId").doesNotExist());

        verify(managementService).createSession(FACULTY_ACCOUNT, 17L, Instant.parse("2026-10-19T04:30:00Z"), "Arrays introduction");
    }

    @Test
    void anInvalidTimeOrTopicIsRefusedBeforeAnythingIsSaved() throws Exception {
        for (String body : new String[] {
                "{\"topic\":\"No time\"}",
                "{\"heldAt\":\"2026-10-19T10:00\"}",
                "{\"heldAt\":\"1999-12-31T10:00:00Z\"}",
                "{\"heldAt\":\"2026-10-19T04:30:00Z\",\"topic\":\"" + "x".repeat(501) + "\"}"}) {
            mockMvc.perform(as(manager(), json(post("/api/attendance/faculty/course-offerings/17/sessions"), body)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(as(manager(), json(put("/api/attendance/faculty/sessions/8"), body)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void savingRecordsSendsTheRecordIdsAndStatusesAndAnswersWithTheRoster() throws Exception {
        when(facultyQueries.getSessionDetail(FACULTY_ACCOUNT, 8L)).thenReturn(detail());

        mockMvc.perform(as(manager(), json(put("/api/attendance/faculty/sessions/8/records"),
                        "{\"records\":[{\"attendanceRecordId\":101,\"status\":\"PRESENT\"},"
                                + "{\"attendanceRecordId\":102,\"status\":\"ABSENT\",\"studentLoginId\":\"2408400100099\"}]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records", hasSize(1)))
                .andExpect(jsonPath("$.records[0].attendanceRecordId").value(101))
                .andExpect(jsonPath("$.records[0].courseEnrollmentId").doesNotExist());

        verify(managementService).saveRecords(FACULTY_ACCOUNT, 8L, List.of(
                new RecordChange(101L, AttendanceStatus.PRESENT), new RecordChange(102L, AttendanceStatus.ABSENT)));
    }

    @Test
    void recordsWithoutAnIdOrWithAnUnknownStatusAreRefused() throws Exception {
        for (String body : new String[] {
                "{}",
                "{\"records\":[{\"status\":\"PRESENT\"}]}",
                "{\"records\":[{\"attendanceRecordId\":101}]}",
                "{\"records\":[{\"attendanceRecordId\":101,\"status\":\"LATE\"}]}",
                "{\"records\":[null]}"}) {
            mockMvc.perform(as(manager(), json(put("/api/attendance/faculty/sessions/8/records"), body)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void refusalsFromTheServiceKeepTheirStatusAndMessage() throws Exception {
        when(managementService.finalizeSession(FACULTY_ACCOUNT, 8L)).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "All students must be marked before finalizing attendance."));
        when(facultyQueries.getSessionDetail(FACULTY_ACCOUNT, 9L)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "Attendance session not found or unavailable."));

        mockMvc.perform(as(manager(), post("/api/attendance/faculty/sessions/8/finalize")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("All students must be marked before finalizing attendance."));
        mockMvc.perform(as(manager(), get("/api/attendance/faculty/sessions/9")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Attendance session not found or unavailable."));
    }

    @Test
    void finalizingAnswersWithTheSession() throws Exception {
        AttendanceSession finalized = session();
        finalized.markFinalized(Instant.parse("2026-10-19T06:00:00Z"));
        when(managementService.finalizeSession(FACULTY_ACCOUNT, 8L)).thenReturn(finalized);

        mockMvc.perform(as(manager(), post("/api/attendance/faculty/sessions/8/finalize")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalizedAt").value("2026-10-19T06:00:00Z"));
    }

    @Test
    void thereIsNoDeleteOrReopen() throws Exception {
        mockMvc.perform(as(manager(), delete("/api/attendance/faculty/sessions/8"))).andExpect(status().is4xxClientError());
        mockMvc.perform(as(manager(), post("/api/attendance/faculty/sessions/8/reopen"))).andExpect(status().is4xxClientError());
        verifyNoInteractions(managementService);
    }

    @Test
    void aStudentWithTheViewPermissionGetsTheirOwnAttendanceFromTheToken() throws Exception {
        when(studentQueries.getMyAttendance(STUDENT_ACCOUNT)).thenReturn(new StudentAttendanceResponse(List.of(
                new StudentAttendanceResponse.Course(17L, "BCS301", "Data Structures", 3, "BTECH", "2026-27", 10, 8, 2,
                        new BigDecimal("80.00"), List.of(new StudentAttendanceResponse.Session(
                                8L, Instant.parse("2026-10-19T04:30:00Z"), "Arrays introduction", "PRESENT"))))));

        mockMvc.perform(as(student("ROLE_STUDENT", "ATTENDANCE_VIEW"), get("/api/attendance/student/me?studentId=12&courseEnrollmentId=5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].attendancePercentage").value(80.0))
                .andExpect(jsonPath("$.courses[0].presentCount").value(8))
                .andExpect(jsonPath("$.courses[0].sessions[0].status").value("PRESENT"))
                .andExpect(jsonPath("$.courses[0].sessions[0].attendanceRecordId").doesNotExist());

        verify(studentQueries).getMyAttendance(STUDENT_ACCOUNT);
    }

    @Test
    void theStudentRouteNeedsTheStudentRoleAndTheViewPermissionTogether() throws Exception {
        for (String token : new String[] {
                student("ROLE_STUDENT", "LMS_COURSE_VIEW"),
                student("ATTENDANCE_VIEW"),
                faculty("ROLE_FACULTY", "ATTENDANCE_MANAGE", "ATTENDANCE_VIEW"),
                faculty("ROLE_ADMIN", "ATTENDANCE_MANAGE", "ATTENDANCE_VIEW")}) {
            mockMvc.perform(as(token, get("/api/attendance/student/me"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/attendance/student/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(studentQueries);
    }

    @Test
    void anAccountWithBothRolesUsesEachSideSeparately() throws Exception {
        String both = TestTokens.valid("2408400100015", 15, "ROLE_STUDENT", "ROLE_FACULTY", "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE");
        when(studentQueries.getMyAttendance(anyLong())).thenReturn(new StudentAttendanceResponse(List.of()));

        mockMvc.perform(as(both, get("/api/attendance/student/me"))).andExpect(status().isOk());
        mockMvc.perform(as(both, get("/api/attendance/faculty/my-courses"))).andExpect(status().isOk());

        verify(studentQueries).getMyAttendance(15L);
        verify(facultyQueries).getMyCourseOfferings(15L);
    }

    @Test
    void theRoutesAreDocumentedWithTheBearerScheme() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : new String[][] {
                {"/api/attendance/faculty/my-courses", "get"},
                {"/api/attendance/faculty/course-offerings/{courseOfferingId}/sessions", "get"},
                {"/api/attendance/faculty/course-offerings/{courseOfferingId}/sessions", "post"},
                {"/api/attendance/faculty/sessions/{sessionId}", "get"},
                {"/api/attendance/faculty/sessions/{sessionId}", "put"},
                {"/api/attendance/faculty/sessions/{sessionId}/records", "put"},
                {"/api/attendance/faculty/sessions/{sessionId}/finalize", "post"},
                {"/api/attendance/student/me", "get"}}) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            String success = operation[1].equals("post") && operation[0].endsWith("/sessions") ? "201" : "200";
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['" + success + "']").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists());
        }
        result.andExpect(jsonPath("$.paths['/api/attendance/faculty/sessions/{sessionId}/records'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/attendance/faculty/sessions/{sessionId}/finalize'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/attendance/faculty/sessions/{sessionId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/attendance/faculty/sessions/{sessionId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AttendanceSessionRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AttendanceRecordResponse.properties.courseEnrollmentId").doesNotExist());
    }
}

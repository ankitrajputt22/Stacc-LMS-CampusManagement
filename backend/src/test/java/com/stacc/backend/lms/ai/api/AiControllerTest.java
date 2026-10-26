package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The AI routes through Spring Security with AI switched off, as it is by default: rules, validation, the 503 answer,
 * and that AI being unavailable neither breaks other routes nor writes any academic or assignment data.
 */
class AiControllerTest extends DatabaseFreeApiTest {

    private static final String STUDENT = token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW", "AI_STUDY_USE");
    private static final String FACULTY = token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW", "AI_TEACHING_USE");

    @MockitoBean
    private LmsStudentAccessService studentAccess;

    @MockitoBean
    private LmsFacultyAccessService facultyAccess;

    @Autowired
    private LmsAssignmentRepository assignments;

    @Autowired
    private LmsAssignmentSubmissionRepository submissions;

    @Autowired
    private LmsSubmissionGradeRepository grades;

    @Autowired
    private CourseResultRepository results;

    @Autowired
    private AttendanceRecordRepository attendance;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // Fresh builders every time: a builder keeps the headers of earlier calls.
    private static List<MockHttpServletRequestBuilder> studentRoutes() {
        return List.of(json(post("/api/lms/courses/7/ai/ask"), "{\"question\":\"What is a key?\"}"),
                post("/api/lms/courses/7/ai/topics/3/summary"),
                json(post("/api/lms/courses/7/ai/practice"), "{\"questionCount\":3}"));
    }

    private static List<MockHttpServletRequestBuilder> facultyRoutes() {
        return List.of(get("/api/lms/faculty/courses/7/ai/status"), post("/api/lms/faculty/courses/7/ai/sync"),
                json(post("/api/lms/faculty/courses/7/ai/assessment-draft"),
                        "{\"questionCount\":3,\"difficulty\":\"EASY\",\"questionType\":\"MCQ\"}"));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
    }

    @BeforeEach
    void access() {
        when(studentAccess.canAccessCourse(any(), any())).thenReturn(true);
        when(facultyAccess.canAccessCourse(any(), any())).thenReturn(true);
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        for (MockHttpServletRequestBuilder route : studentRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder route : facultyRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void studentRoutesNeedStudentLmsCourseViewAndAiStudyUse() throws Exception {
        for (String denied : List.of(token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW"), token(70, "ROLE_STUDENT", "AI_STUDY_USE"),
                token(80, "ROLE_FACULTY", "LMS_COURSE_VIEW", "AI_STUDY_USE", "AI_TEACHING_USE", "FACULTY_COURSE_VIEW"),
                token(1, "ROLE_ADMIN", "LMS_COURSE_VIEW", "AI_STUDY_USE"))) {
            for (MockHttpServletRequestBuilder route : studentRoutes()) {
                assertThat(statusOf(route, denied)).isEqualTo(403);
            }
        }
        for (MockHttpServletRequestBuilder route : studentRoutes()) {
            // Allowed through, and then AI is switched off.
            assertThat(statusOf(route, STUDENT)).isEqualTo(503);
        }
    }

    @Test
    void facultyRoutesNeedFacultyCourseViewAndAiTeachingUse() throws Exception {
        for (String denied : List.of(token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"), token(80, "ROLE_FACULTY", "AI_TEACHING_USE"),
                token(70, "ROLE_STUDENT", "FACULTY_COURSE_VIEW", "AI_TEACHING_USE", "LMS_COURSE_VIEW", "AI_STUDY_USE"),
                token(1, "ROLE_ADMIN", "FACULTY_COURSE_VIEW", "AI_TEACHING_USE"))) {
            for (MockHttpServletRequestBuilder route : facultyRoutes()) {
                assertThat(statusOf(route, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void aiSwitchedOffAnswers503WithAPlainMessage() throws Exception {
        mockMvc.perform(json(post("/api/lms/courses/7/ai/ask"), "{\"question\":\"What is a key?\"}")
                        .header(HttpHeaders.AUTHORIZATION, STUDENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("AI learning features are temporarily unavailable."));
        mockMvc.perform(post("/api/lms/faculty/courses/7/ai/sync").header(HttpHeaders.AUTHORIZATION, FACULTY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void facultyStatusStillWorksWithAiSwitchedOff() throws Exception {
        mockMvc.perform(get("/api/lms/faculty/courses/7/ai/status").header(HttpHeaders.AUTHORIZATION, FACULTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.status").value("NOT_INDEXED"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.publishedSourceCount").value(0));
    }

    @Test
    void inaccessibleCoursesAre404BeforeAnythingElse() throws Exception {
        when(studentAccess.canAccessCourse(any(), any())).thenReturn(false);
        when(facultyAccess.canAccessCourse(any(), any())).thenReturn(false);
        for (MockHttpServletRequestBuilder route : studentRoutes()) {
            assertThat(statusOf(route, STUDENT)).isEqualTo(404);
        }
        for (MockHttpServletRequestBuilder route : facultyRoutes()) {
            assertThat(statusOf(route, FACULTY)).isEqualTo(404);
        }
    }

    @Test
    void requestsAreValidated() throws Exception {
        for (String body : List.of("{\"questionCount\":0}", "{\"questionCount\":11}", "{\"topicId\":0}")) {
            assertThat(statusOf(json(post("/api/lms/courses/7/ai/practice"), body), STUDENT)).isEqualTo(400);
        }
        for (String body : List.of("{\"questionCount\":1}", "{\"questionCount\":10}", "{}")) {
            assertThat(statusOf(json(post("/api/lms/courses/7/ai/practice"), body), STUDENT)).isEqualTo(503);
        }
        for (String body : List.of("{}", "{\"question\":\"\"}", "{\"question\":\"" + "x".repeat(1001) + "\"}")) {
            assertThat(statusOf(json(post("/api/lms/courses/7/ai/ask"), body), STUDENT)).isEqualTo(400);
        }
        for (String body : List.of(
                "{\"questionCount\":0,\"difficulty\":\"EASY\",\"questionType\":\"MCQ\"}",
                "{\"questionCount\":21,\"difficulty\":\"EASY\",\"questionType\":\"MCQ\"}",
                "{\"questionCount\":3,\"difficulty\":\"IMPOSSIBLE\",\"questionType\":\"MCQ\"}",
                "{\"questionCount\":3,\"difficulty\":\"EASY\",\"questionType\":\"ESSAY\"}",
                "{\"questionCount\":3,\"questionType\":\"MCQ\"}",
                "{\"questionCount\":3,\"difficulty\":\"EASY\",\"questionType\":\"MCQ\",\"additionalInstruction\":\"" + "x".repeat(501) + "\"}")) {
            assertThat(statusOf(json(post("/api/lms/faculty/courses/7/ai/assessment-draft"), body), FACULTY)).isEqualTo(400);
        }
        assertThat(statusOf(json(post("/api/lms/faculty/courses/7/ai/assessment-draft"),
                "{\"questionCount\":20,\"difficulty\":\"MIXED\",\"questionType\":\"MIXED\"}"), FACULTY)).isEqualTo(503);
    }

    @Test
    void aiBeingUnavailableBreaksNothingElseAndWritesNothing() throws Exception {
        for (MockHttpServletRequestBuilder route : studentRoutes()) {
            statusOf(route, STUDENT);
        }
        for (MockHttpServletRequestBuilder route : facultyRoutes()) {
            statusOf(route, FACULTY);
        }
        // The student's course list does not depend on AI.
        mockMvc.perform(get("/api/lms/my-courses").header(HttpHeaders.AUTHORIZATION, STUDENT)).andExpect(status().isOk());
        verifyNoInteractions(assignments, submissions, grades, results, attendance);
    }
}

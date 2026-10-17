package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the faculty review and grading routes through Spring Security. The services are stand-ins, so these
 * tests are about who gets in, what reaches the services, and what is sent back.
 */
class LmsFacultySubmissionControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final byte[] PDF = "%PDF-1.4 made-up test file".getBytes(StandardCharsets.US_ASCII);

    @MockitoBean
    private LmsFacultySubmissionQueryService queryService;

    @MockitoBean
    private LmsAssignmentGradingService gradingService;

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("TEST-FACULTY-1", ACCOUNT_ID, authorities);
    }

    private static String graderToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder gradeRequest(String body) {
        return put("/api/lms/faculty/submission-attempts/31/grade").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(
                get("/api/lms/faculty/assignments/5/submissions"),
                get("/api/lms/faculty/assignments/5/submissions/12"),
                get("/api/lms/faculty/submission-attachments/40/download"),
                gradeRequest("{\"marksAwarded\":18}"));
    }

    private static LmsSubmissionGrade grade(String marks, String feedback) {
        LmsSubmissionAttempt attempt = new LmsSubmissionAttempt(mock(LmsAssignmentSubmission.class), 1, "Answer",
                Instant.parse("2026-10-20T17:00:00Z"), Instant.parse("2026-10-20T18:00:00Z"));
        return new LmsSubmissionGrade(attempt, mock(FacultyProfile.class), new BigDecimal(marks), new BigDecimal("20"),
                feedback, Instant.parse("2026-10-21T04:00:00Z"));
    }

    @Test
    void graderSeesEachStudentsLatestAttemptByLoginIdOnly() throws Exception {
        when(queryService.listSubmissions(ACCOUNT_ID, 5L)).thenReturn(List.of(new LmsFacultySubmissionSummaryResponse(
                12L, "2408400100011", 2, 2, Instant.parse("2026-10-20T17:04:00Z"), false, false, null, null)));

        mockMvc.perform(as(graderToken(), get("/api/lms/faculty/assignments/5/submissions").param("facultyProfileId", "999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].*", hasSize(9)))
                .andExpect(jsonPath("$[0].studentLoginId").value("2408400100011"))
                .andExpect(jsonPath("$[0].latestGraded").value(false))
                .andExpect(content().string(not(containsString("courseEnrollment"))));

        verify(queryService).listSubmissions(ACCOUNT_ID, 5L);
    }

    @Test
    void detailShowsAttemptsWithFilesAndGradesButNoStorageKeys() throws Exception {
        when(queryService.submissionDetail(ACCOUNT_ID, 5L, 12L)).thenReturn(new LmsFacultySubmissionDetailResponse(
                5L, "Arrays practice", new BigDecimal("20.00"), Instant.parse("2026-10-20T18:00:00Z"), "2408400100011", 12L,
                List.of(new LmsFacultySubmissionAttemptResponse(31L, 2, "Answer\nLine two", Instant.parse("2026-10-20T19:00:00Z"),
                        Instant.parse("2026-10-20T18:00:00Z"), true,
                        List.of(new LmsSubmissionAttachmentResponse(40L, "answer.pdf", "application/pdf", 9)),
                        LmsSubmissionGradeResponse.of(grade("18", "Good."))))));

        mockMvc.perform(as(graderToken(), get("/api/lms/faculty/assignments/5/submissions/12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentLoginId").value("2408400100011"))
                .andExpect(jsonPath("$.attempts[0].late").value(true))
                .andExpect(jsonPath("$.attempts[0].textContent").value("Answer\nLine two"))
                .andExpect(jsonPath("$.attempts[0].attachments[0].*", hasSize(4)))
                .andExpect(jsonPath("$.attempts[0].grade.*", hasSize(4)))
                .andExpect(content().string(containsString("\"marksAwarded\":18.00")))
                .andExpect(content().string(not(containsString("objectKey"))))
                .andExpect(content().string(not(containsString("gradedBy"))));
    }

    @Test
    void graderDownloadsAStudentsFileAsAnAttachment() throws Exception {
        when(queryService.openAttachment(ACCOUNT_ID, 40L)).thenReturn(new LmsAssignmentSubmissionService.Download(
                "answer.pdf", "application/pdf", PDF.length, new ByteArrayInputStream(PDF)));

        mockMvc.perform(as(graderToken(), get("/api/lms/faculty/submission-attachments/40/download")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void gradingTakesTheMarksAndFeedbackOnlyAndAnswersTheGrade() throws Exception {
        when(gradingService.grade(ACCOUNT_ID, 31L, new BigDecimal("18.5"), "Good.\nExplain question 4."))
                .thenReturn(grade("18.5", "Good.\nExplain question 4."));

        mockMvc.perform(as(graderToken(), gradeRequest("{\"marksAwarded\":18.5,\"feedback\":\"Good.\\nExplain question 4.\","
                        + "\"maxMarksSnapshot\":100,\"gradedAt\":\"2020-01-01T00:00:00Z\",\"facultyProfileId\":9,\"attemptId\":77}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.gradedAt").value("2026-10-21T04:00:00Z"))
                .andExpect(content().string(containsString("\"marksAwarded\":18.50")))
                .andExpect(content().string(containsString("\"maxMarksSnapshot\":20.00")));

        verify(gradingService).grade(ACCOUNT_ID, 31L, new BigDecimal("18.5"), "Good.\nExplain question 4.");
    }

    @Test
    void invalidMarksOrFeedbackAreFieldErrorsBeforeAnyWork() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"feedback\":\"Only feedback\"}",
                "{\"marksAwarded\":-1}",
                "{\"marksAwarded\":12.555}",
                "{\"marksAwarded\":5,\"feedback\":\"" + "x".repeat(10_001) + "\"}")) {
            mockMvc.perform(as(graderToken(), gradeRequest(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
        verifyNoInteractions(gradingService);
    }

    @Test
    void serviceRefusalsKeepTheirStatusAndSafeMessage() throws Exception {
        when(gradingService.grade(anyLong(), eq(31L), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Marks cannot exceed 20."));
        when(queryService.submissionDetail(ACCOUNT_ID, 5L, 99L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Submission not found or unavailable."));

        mockMvc.perform(as(graderToken(), gradeRequest("{\"marksAwarded\":21}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Marks cannot exceed 20."));
        mockMvc.perform(as(graderToken(), get("/api/lms/faculty/assignments/5/submissions/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Submission not found or unavailable."));
    }

    @Test
    void everyRouteNeedsTheFacultyRoleTheCourseViewAndTheGradePermission() throws Exception {
        for (MockHttpServletRequestBuilder route : routes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(
                tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "LMS_ASSIGNMENT_GRADE"),
                tokenWith("FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE"),
                tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT", "LMS_ASSIGNMENT_GRADE"),
                tokenWith("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE"))) {
            for (MockHttpServletRequestBuilder route : routes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(queryService, gradingService);
    }

    @Test
    void studentOwningAFileStillCannotUseTheFacultyDownload() throws Exception {
        mockMvc.perform(as(TestTokens.valid("TEST-STUDENT-1", 12L, "ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT"),
                        get("/api/lms/faculty/submission-attachments/40/download")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queryService);
    }

    @Test
    void gradesCannotBeDeleted() throws Exception {
        mockMvc.perform(as(graderToken(), delete("/api/lms/faculty/submission-attempts/31/grade")))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(gradingService);
    }

    @Test
    void reviewAndGradingRoutesAppearInTheApiDocumentation() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : new String[][] {
                {"/api/lms/faculty/assignments/{assignmentId}/submissions", "get"},
                {"/api/lms/faculty/assignments/{assignmentId}/submissions/{submissionId}", "get"},
                {"/api/lms/faculty/submission-attachments/{attachmentId}/download", "get"},
                {"/api/lms/faculty/submission-attempts/{attemptId}/grade", "put"}}) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['200']").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists())
                    .andExpect(jsonPath(path + ".responses['404']").exists());
        }
        result.andExpect(jsonPath("$.paths['/api/lms/faculty/submission-attempts/{attemptId}/grade'].put.responses['400']").exists())
                .andExpect(jsonPath("$.components.schemas.LmsSubmissionAttemptResponse.properties.grade").exists())
                .andExpect(jsonPath("$.components.schemas.LmsSubmissionGradeResponse.properties.gradedByFacultyProfileId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsGradeSubmissionAttemptRequest.properties.maxMarksSnapshot").doesNotExist());
    }
}

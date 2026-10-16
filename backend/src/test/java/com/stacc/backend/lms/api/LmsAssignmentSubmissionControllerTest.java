package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService.SubmittedAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachment;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

/**
 * Calls the submission routes through Spring Security. The service is a stand-in, so these tests are about
 * who gets in, what is taken from the token, what reaches the service, and what is sent back.
 */
class LmsAssignmentSubmissionControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 12;
    private static final String SUBMISSIONS = "/api/lms/courses/7/assignments/5/submissions";
    private static final byte[] PDF = "%PDF-1.4 made-up test file".getBytes(StandardCharsets.US_ASCII);

    @MockitoBean
    private LmsAssignmentSubmissionService submissionService;

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("TEST-STUDENT-1", ACCOUNT_ID, authorities);
    }

    private static String studentToken() {
        return tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT");
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<?>> T as(String token, T request) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private static MockMultipartHttpServletRequestBuilder submitWithText(String text) {
        return (MockMultipartHttpServletRequestBuilder) multipart(SUBMISSIONS).param("textContent", text);
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF);
    }

    private static SubmittedAttempt submitted(int number, String text, boolean late, String... fileNames) {
        LmsSubmissionAttempt attempt = new LmsSubmissionAttempt(mock(LmsAssignmentSubmission.class), number, text,
                Instant.parse(late ? "2026-10-20T19:00:00Z" : "2026-10-20T17:00:00Z"), Instant.parse("2026-10-20T18:00:00Z"));
        ReflectionTestUtils.setField(attempt, "id", 100L + number);
        List<LmsSubmissionAttachment> files = new ArrayList<>();
        for (int index = 0; index < fileNames.length; index++) {
            LmsSubmissionAttachment file = new LmsSubmissionAttachment(attempt, "lms/submissions/secret-key-" + index,
                    fileNames[index], "application/pdf", PDF.length);
            ReflectionTestUtils.setField(file, "id", 200L + index);
            files.add(file);
        }
        return new SubmittedAttempt(attempt, files);
    }

    private static List<AbstractMockHttpServletRequestBuilder<?>> routes() {
        return List.of(submitWithText("Answer"), get(SUBMISSIONS + "/me"), get("/api/lms/submission-attachments/200/download"));
    }

    @Test
    void textOnlyAttemptAnswersCreatedForTheAccountInTheToken() throws Exception {
        when(submissionService.submit(eq(ACCOUNT_ID), eq(7L), eq(5L), eq("My answer\nLine two"), any()))
                .thenReturn(submitted(1, "My answer\nLine two", false));

        mockMvc.perform(as(studentToken(), submitWithText("My answer\nLine two")
                        .param("courseEnrollmentId", "999")
                        .param("studentProfileId", "999")
                        .param("userAccountId", "999")
                        .param("submittedAt", "2020-01-01T00:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.attemptId").value(101))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.textContent").value("My answer\nLine two"))
                .andExpect(jsonPath("$.submittedAt").value("2026-10-20T17:00:00Z"))
                .andExpect(jsonPath("$.late").value(false))
                .andExpect(jsonPath("$.attachments", hasSize(0)));

        verify(submissionService).submit(eq(ACCOUNT_ID), eq(7L), eq(5L), eq("My answer\nLine two"), any());
    }

    @Test
    void everyFilePartReachesTheServiceAndTheAnswerNeverShowsWhereFilesAreStored() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        when(submissionService.submit(eq(ACCOUNT_ID), eq(7L), eq(5L), isNull(), files.capture()))
                .thenReturn(submitted(2, null, true, "a.pdf", "b.pdf"));

        mockMvc.perform(as(studentToken(), multipart(SUBMISSIONS).file(pdf("a.pdf")).file(pdf("b.pdf"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.late").value(true))
                .andExpect(jsonPath("$.textContent").doesNotExist())
                .andExpect(jsonPath("$.attachments[0].*", hasSize(4)))
                .andExpect(jsonPath("$.attachments[0].attachmentId").value(200))
                .andExpect(jsonPath("$.attachments[1].originalFileName").value("b.pdf"))
                .andExpect(content().string(not(containsString("secret-key"))))
                .andExpect(content().string(not(containsString("objectKey"))));

        List<String> names = files.getValue().stream().map(MultipartFile::getOriginalFilename).toList();
        assertEquals(List.of("a.pdf", "b.pdf"), names);
    }

    @Test
    void textLongerThanTheLimitIsAFieldErrorBeforeAnyWork() throws Exception {
        mockMvc.perform(as(studentToken(), submitWithText("x".repeat(LmsSubmissionAttempt.MAX_TEXT_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("textContent"));
        verifyNoInteractions(submissionService);
    }

    @Test
    void serviceRefusalsKeepTheirStatusAndSafeMessage() throws Exception {
        when(submissionService.submit(anyLong(), eq(7L), eq(5L), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Add a text response or at least one file."));
        when(submissionService.history(ACCOUNT_ID, 7L, 6L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Assignment not found or unavailable."));

        mockMvc.perform(as(studentToken(), multipart(SUBMISSIONS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add a text response or at least one file."));
        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/assignments/6/submissions/me")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Assignment not found or unavailable."));
    }

    @Test
    void everyRouteNeedsTheStudentRoleTheCourseViewAndTheSubmitPermission() throws Exception {
        for (AbstractMockHttpServletRequestBuilder<?> route : routes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(
                tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW"),
                tokenWith("ROLE_STUDENT", "LMS_ASSIGNMENT_SUBMIT"),
                tokenWith("LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT"),
                tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE", "LMS_ASSIGNMENT_SUBMIT"),
                tokenWith("ROLE_ADMIN", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT"))) {
            for (AbstractMockHttpServletRequestBuilder<?> route : routes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(submissionService);
    }

    @Test
    void multiRoleAccountWithTheStudentAuthoritiesMaySubmit() throws Exception {
        when(submissionService.submit(eq(ACCOUNT_ID), eq(7L), eq(5L), any(), any())).thenReturn(submitted(1, "Answer", false));

        mockMvc.perform(as(tokenWith("ROLE_FACULTY", "ROLE_STUDENT", "FACULTY_COURSE_VIEW", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT"),
                        submitWithText("Answer")))
                .andExpect(status().isCreated());
    }

    @Test
    void historyListsTheAttemptsInTheServicesNewestFirstOrderAndIsEmptyWithoutAny() throws Exception {
        when(submissionService.history(ACCOUNT_ID, 7L, 5L)).thenReturn(List.of(submitted(2, null, true, "b.pdf"), submitted(1, "First", false)));
        when(submissionService.history(ACCOUNT_ID, 7L, 9L)).thenReturn(List.of());

        mockMvc.perform(as(studentToken(), get(SUBMISSIONS + "/me").param("courseEnrollmentId", "999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].attemptNumber").value(2))
                .andExpect(jsonPath("$.attempts[0].late").value(true))
                .andExpect(jsonPath("$.attempts[0].attachments[0].originalFileName").value("b.pdf"))
                .andExpect(jsonPath("$.attempts[1].attemptNumber").value(1))
                .andExpect(jsonPath("$.attempts[1].late").value(false))
                .andExpect(content().string(not(containsString("secret-key"))));
        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/assignments/9/submissions/me")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"attempts\":[]}"));

        verify(submissionService).history(ACCOUNT_ID, 7L, 5L);
    }

    @Test
    void ownFileIsSentAsAnAttachmentWithItsStoredDetails() throws Exception {
        when(submissionService.openOwnAttachment(ACCOUNT_ID, 200L)).thenReturn(new LmsAssignmentSubmissionService.Download(
                "Week 1 – Answers.pdf", "application/pdf", PDF.length, new ByteArrayInputStream(PDF)));

        mockMvc.perform(as(studentToken(), get("/api/lms/submission-attachments/200/download")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''Week%201%20%E2%80%93%20Answers.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void anotherStudentsFileLooksLikeAMissingOne() throws Exception {
        when(submissionService.openOwnAttachment(ACCOUNT_ID, 300L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "File not found or unavailable."));

        mockMvc.perform(as(studentToken(), get("/api/lms/submission-attachments/300/download")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("File not found or unavailable."));
    }

    @Test
    void attemptsCannotBeChangedOrDeleted() throws Exception {
        mockMvc.perform(as(studentToken(), delete(SUBMISSIONS + "/me"))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(as(studentToken(), put(SUBMISSIONS).contentType("application/json").content("{}")))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(as(studentToken(), delete("/api/lms/submission-attachments/200/download")))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(submissionService);
    }

    @Test
    void submissionRoutesAppearInTheApiDocumentation() throws Exception {
        String submit = "$.paths['/api/lms/courses/{lmsCourseId}/assignments/{assignmentId}/submissions'].post";
        String history = "$.paths['/api/lms/courses/{lmsCourseId}/assignments/{assignmentId}/submissions/me'].get";
        String download = "$.paths['/api/lms/submission-attachments/{attachmentId}/download'].get";
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(submit + ".requestBody.content['multipart/form-data'].schema['$ref']")
                        .value("#/components/schemas/LmsSubmissionRequest"))
                .andExpect(jsonPath("$.components.schemas.LmsSubmissionRequest.properties.textContent.maxLength").value(20000))
                .andExpect(jsonPath("$.components.schemas.LmsSubmissionRequest.properties.files.items.format").value("binary"));
        for (String[] operation : new String[][] {
                {submit, "201"}, {submit, "400"}, {submit, "404"}, {submit, "413"},
                {history, "200"}, {history, "404"}, {download, "200"}, {download, "404"}}) {
            result.andExpect(jsonPath(operation[0] + ".responses['" + operation[1] + "']").exists());
        }
        for (String operation : List.of(submit, history, download)) {
            result.andExpect(jsonPath(operation + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(operation + ".responses['401']").exists())
                    .andExpect(jsonPath(operation + ".responses['403']").exists());
        }
        result.andExpect(jsonPath("$.components.schemas.LmsSubmissionAttachmentResponse.properties.objectKey").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsSubmissionAttemptResponse.properties.courseEnrollmentId").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/assignments/{assignmentId}/submissions']").doesNotExist());
    }

    @Test
    void anyStudentOnlyEverReachesTheServiceWithTheirOwnAccount() throws Exception {
        when(submissionService.submit(anyLong(), anyLong(), anyLong(), any(), anyList())).thenReturn(submitted(1, "x", false));
        when(submissionService.submit(anyLong(), anyLong(), anyLong(), any(), isNull())).thenReturn(submitted(1, "x", false));

        mockMvc.perform(as(TestTokens.valid("TEST-STUDENT-2", 13L, "ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT"),
                submitWithText("x").param("userAccountId", "12"))).andExpect(status().isCreated());

        verify(submissionService).submit(eq(13L), eq(7L), eq(5L), eq("x"), any());
    }
}

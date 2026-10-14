package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Calls the course-page route through Spring Security. The query service is a stand-in, so these
 * tests are about who gets in, what is asked, and what is sent back. Which courses a student may
 * open is tested with the query service.
 */
class LmsCourseDetailControllerTest extends DatabaseFreeApiTest {

    private static final String COURSE = "/api/lms/courses/7";

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "TEST-STUDENT-1";
    private static final long ACCOUNT_ID = 12;

    @MockitoBean
    private LmsStudentCourseQueryService courseQueryService;

    private static String tokenWith(String... authorities) {
        return TestTokens.valid(LOGIN_ID, ACCOUNT_ID, authorities);
    }

    private static String studentToken() {
        return tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW");
    }

    private ResultActions getAs(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static LmsCourseDetailResponse aCourse() {
        return new LmsCourseDetailResponse(
                7L, "TST301", "Test Course One", new BigDecimal("4.00"),
                "TST", "Test Department", "BTECH", "Test Program", 3, "2026-27");
    }

    private void expectForbidden(String token) throws Exception {
        getAs(token, COURSE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."));
    }

    @Test
    void studentGetsTheCourseWithItsAcademicDetails() throws Exception {
        when(courseQueryService.getAccessibleCourse(ACCOUNT_ID, 7L)).thenReturn(aCourse());

        getAs(studentToken(), COURSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lmsCourseId").value(7))
                .andExpect(jsonPath("$.courseCode").value("TST301"))
                .andExpect(jsonPath("$.courseName").value("Test Course One"))
                .andExpect(jsonPath("$.credits").value(4.00))
                .andExpect(jsonPath("$.departmentCode").value("TST"))
                .andExpect(jsonPath("$.departmentName").value("Test Department"))
                .andExpect(jsonPath("$.programCode").value("BTECH"))
                .andExpect(jsonPath("$.programName").value("Test Program"))
                .andExpect(jsonPath("$.semesterNumber").value(3))
                .andExpect(jsonPath("$.academicSessionCode").value("2026-27"));
    }

    @Test
    void responseHoldsOnlyCourseInformation() throws Exception {
        when(courseQueryService.getAccessibleCourse(ACCOUNT_ID, 7L)).thenReturn(aCourse());

        getAs(studentToken(), COURSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(10)))
                .andExpect(content().string(not(containsString(LOGIN_ID))))
                .andExpect(content().string(not(containsString("accountId"))))
                .andExpect(content().string(not(containsString("Enrollment"))))
                .andExpect(content().string(not(containsString("membership"))))
                .andExpect(content().string(not(containsString("status"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void courseIsAskedForTheAccountInTheTokenAndTheIdInThePath() throws Exception {
        when(courseQueryService.getAccessibleCourse(ACCOUNT_ID, 7L)).thenReturn(aCourse());

        mockMvc.perform(get(COURSE)
                        .param("accountId", "999")
                        .param("studentProfileId", "999")
                        .param("courseEnrollmentId", "999")
                        .param("lmsCourseId", "8")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken()))
                .andExpect(status().isOk());

        verify(courseQueryService).getAccessibleCourse(ACCOUNT_ID, 7L);
        verifyNoMoreInteractions(courseQueryService);
    }

    @Test
    void unavailableCourseIsNotFoundWithoutSayingWhy() throws Exception {
        when(courseQueryService.getAccessibleCourse(ACCOUNT_ID, 7L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Course not found or unavailable."));

        getAs(studentToken(), COURSE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Course not found or unavailable."))
                .andExpect(jsonPath("$.path").value(COURSE))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void requestWithoutAValidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(COURSE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
        getAs("not-a-token", COURSE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void accountsWithoutTheStudentRoleAndThePermissionAreForbidden() throws Exception {
        expectForbidden(tokenWith("ROLE_STUDENT"));
        expectForbidden(tokenWith("LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_FACULTY", "LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_ADMIN"));
        expectForbidden(tokenWith("ROLE_ADMIN", "LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_STUDENT", "ROLE_LMS_COURSE_VIEW"));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void courseIdMustBeANumber() throws Exception {
        getAs(studentToken(), "/api/lms/courses/abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        getAs(studentToken(), "/api/lms/courses/99999999999999999999")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void routeOnlyAnswersGet() throws Exception {
        mockMvc.perform(post(COURSE).header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void routeAppearsInTheApiDocumentationAsABearerTokenOperation() throws Exception {
        String operation = "$.paths['/api/lms/courses/{lmsCourseId}'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("Get an accessible LMS course"))
                .andExpect(jsonPath(operation + ".security[0].bearerAuth").exists())
                .andExpect(jsonPath(operation + ".parameters", hasSize(1)))
                .andExpect(jsonPath(operation + ".parameters[0].name").value("lmsCourseId"))
                .andExpect(jsonPath(operation + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.LmsCourseDetailResponse.properties.*", hasSize(10)));
    }
}

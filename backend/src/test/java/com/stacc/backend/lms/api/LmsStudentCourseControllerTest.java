package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.security.StaccUserPrincipal;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Calls the real route through Spring Security. The query service is a stand-in, so these tests
 * are about who gets in and what is sent back. Which courses a student gets is tested separately.
 */
class LmsStudentCourseControllerTest extends DatabaseFreeApiTest {

    private static final String MY_COURSES = "/api/lms/my-courses";

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "TEST-STUDENT-1";
    private static final long ACCOUNT_ID = 12;

    @MockitoBean
    private LmsStudentCourseQueryService courseQueryService;

    @Autowired
    private AccessTokenService accessTokenService;

    private static String tokenWith(String... authorities) {
        return TestTokens.valid(LOGIN_ID, ACCOUNT_ID, authorities);
    }

    private static String studentToken() {
        return tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW");
    }

    private ResultActions getMyCourses(String token) throws Exception {
        return mockMvc.perform(get(MY_COURSES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void expectForbidden(String token) throws Exception {
        getMyCourses(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.path").value(MY_COURSES))
                .andExpect(content().string(not(containsString("LMS_COURSE_VIEW"))));
    }

    @Test
    void studentWithThePermissionGetsTheirCourses() throws Exception {
        when(courseQueryService.getAccessibleCourses(ACCOUNT_ID)).thenReturn(List.of(
                new LmsCourseSummaryResponse(7L, "TST301", "Test Course One", new BigDecimal("4.00"), 3, "BTECH", "2026-27"),
                new LmsCourseSummaryResponse(8L, "TST302", "Test Course Two", new BigDecimal("1.50"), 3, "BTECH", "2026-27")));

        getMyCourses(studentToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].lmsCourseId").value(7))
                .andExpect(jsonPath("$[0].courseCode").value("TST301"))
                .andExpect(jsonPath("$[0].courseName").value("Test Course One"))
                .andExpect(jsonPath("$[0].credits").value(4.00))
                .andExpect(jsonPath("$[0].semesterNumber").value(3))
                .andExpect(jsonPath("$[0].programCode").value("BTECH"))
                .andExpect(jsonPath("$[0].academicSessionCode").value("2026-27"))
                .andExpect(jsonPath("$[1].lmsCourseId").value(8))
                .andExpect(jsonPath("$[1].credits").value(1.50));
    }

    @Test
    void responseHoldsOnlyTheCourseSummaryFields() throws Exception {
        when(courseQueryService.getAccessibleCourses(ACCOUNT_ID)).thenReturn(List.of(
                new LmsCourseSummaryResponse(7L, "TST301", "Test Course One", new BigDecimal("4.00"), 3, "BTECH", "2026-27")));

        getMyCourses(studentToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].*", hasSize(7)))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString(LOGIN_ID))))
                .andExpect(content().string(not(containsString("accountId"))))
                .andExpect(content().string(not(containsString("authorities"))))
                .andExpect(content().string(not(containsString("Enrollment"))))
                .andExpect(content().string(not(containsString("membership"))))
                .andExpect(content().string(not(containsString("status"))));
    }

    @Test
    void studentWithNoCoursesGetsAnEmptyListNotAnError() throws Exception {
        when(courseQueryService.getAccessibleCourses(ACCOUNT_ID)).thenReturn(List.of());

        getMyCourses(studentToken())
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void coursesAreAskedForTheAccountInTheTokenWhateverTheRequestSays() throws Exception {
        mockMvc.perform(get(MY_COURSES)
                        .param("accountId", "999")
                        .param("userAccountId", "999")
                        .param("studentId", "999")
                        .param("studentProfileId", "999")
                        .param("loginId", "SOMEONE-ELSE")
                        .param("courseEnrollmentId", "999")
                        .header("X-Account-Id", "999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken()))
                .andExpect(status().isOk());

        verify(courseQueryService).getAccessibleCourses(ACCOUNT_ID);
        verifyNoMoreInteractions(courseQueryService);
    }

    @Test
    void requestWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(MY_COURSES))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void badOrExpiredTokenIsUnauthorized() throws Exception {
        getMyCourses("not-a-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
        getMyCourses(TestTokens.expired(LOGIN_ID, ACCOUNT_ID, "ROLE_STUDENT", "LMS_COURSE_VIEW"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void studentWithoutThePermissionIsForbidden() throws Exception {
        expectForbidden(tokenWith("ROLE_STUDENT"));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void permissionWithoutTheStudentRoleIsForbidden() throws Exception {
        expectForbidden(tokenWith("LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_FACULTY", "LMS_COURSE_VIEW"));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void adminGetsNoAutomaticPass() throws Exception {
        expectForbidden(tokenWith("ROLE_ADMIN"));
        expectForbidden(tokenWith("ROLE_ADMIN", "LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_ADMIN", "ROLE_FACULTY", "LMS_COURSE_VIEW"));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void permissionNameMustMatchExactlyWithNoPrefix() throws Exception {
        expectForbidden(tokenWith("ROLE_STUDENT", "ROLE_LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_STUDENT", "SCOPE_LMS_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_STUDENT", "lms_course_view"));
        expectForbidden(tokenWith("STUDENT", "LMS_COURSE_VIEW"));

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void accountHoldingOtherRolesAsWellIsStillAllowed() throws Exception {
        getMyCourses(tokenWith("ROLE_ADMIN", "ROLE_STUDENT", "LMS_COURSE_VIEW")).andExpect(status().isOk());

        verify(courseQueryService).getAccessibleCourses(ACCOUNT_ID);
    }

    @Test
    void tokenFromARealLoginCarriesThePermissionThroughTheStudentRole() throws Exception {
        Role studentRole = new Role(RoleName.STUDENT);
        studentRole.assignPermission(new Permission("LMS_COURSE_VIEW"));
        UserAccount student = new UserAccount(LOGIN_ID, "example-hash-value");
        ReflectionTestUtils.setField(student, "id", ACCOUNT_ID);
        student.assignRole(studentRole);

        // A token issued before the role held the permission does not carry it.
        UserAccount signedInEarlier = new UserAccount("TEST-STUDENT-2", "example-hash-value");
        ReflectionTestUtils.setField(signedInEarlier, "id", 13L);
        signedInEarlier.assignRole(new Role(RoleName.STUDENT));

        getMyCourses(accessTokenService.issue(StaccUserPrincipal.from(student)).value()).andExpect(status().isOk());
        expectForbidden(accessTokenService.issue(StaccUserPrincipal.from(signedInEarlier)).value());

        verify(courseQueryService).getAccessibleCourses(ACCOUNT_ID);
        verifyNoMoreInteractions(courseQueryService);
    }

    @Test
    void tokenWithoutAUsableAccountIdIsRefusedInsteadOfFailing() throws Exception {
        String textAccountId = TestTokens.withClaims(
                TestTokens.validClaims(LOGIN_ID, ACCOUNT_ID, "ROLE_STUDENT", "LMS_COURSE_VIEW"),
                claims -> claims.claim(AccessTokenService.ACCOUNT_ID_CLAIM, "not-a-number"));
        String fractionalAccountId = TestTokens.withClaims(
                TestTokens.validClaims(LOGIN_ID, ACCOUNT_ID, "ROLE_STUDENT", "LMS_COURSE_VIEW"),
                claims -> claims.claim(AccessTokenService.ACCOUNT_ID_CLAIM, 12.5));

        for (String token : List.of(textAccountId, fractionalAccountId)) {
            getMyCourses(token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
        }

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void routeOnlyAnswersGet() throws Exception {
        mockMvc.perform(post(MY_COURSES).header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("The request method is not supported for this resource."));
        mockMvc.perform(post(MY_COURSES)).andExpect(status().isUnauthorized());

        verifyNoInteractions(courseQueryService);
    }

    @Test
    void requestCreatesNoSessionAndSetsNoCookie() throws Exception {
        MvcResult result = getMyCourses(studentToken()).andExpect(status().isOk()).andReturn();

        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void routeAppearsInTheApiDocumentationAsABearerTokenOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].get.summary")
                        .value("Get the authenticated student's accessible LMS courses"))
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/my-courses'].post").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsCourseSummaryResponse.properties.*", hasSize(7)))
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }
}

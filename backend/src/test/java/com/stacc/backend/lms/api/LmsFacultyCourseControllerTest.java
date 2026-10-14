package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.times;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Calls the real route through Spring Security. The query services are stand-ins, so these tests
 * are about who gets in and what is sent back. Which courses a faculty member gets is tested separately.
 */
class LmsFacultyCourseControllerTest extends DatabaseFreeApiTest {

    private static final String FACULTY_MY_COURSES = "/api/lms/faculty/my-courses";

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "TEST-FACULTY-1";
    private static final long ACCOUNT_ID = 21;

    @MockitoBean
    private LmsFacultyCourseQueryService facultyCourseQueryService;

    @MockitoBean
    private LmsStudentCourseQueryService studentCourseQueryService;

    @Autowired
    private AccessTokenService accessTokenService;

    private static String tokenWith(String... authorities) {
        return TestTokens.valid(LOGIN_ID, ACCOUNT_ID, authorities);
    }

    private static String facultyToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW");
    }

    private ResultActions getAs(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions getMyCourses(String token) throws Exception {
        return getAs(FACULTY_MY_COURSES, token);
    }

    private void expectForbidden(String token) throws Exception {
        getMyCourses(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.path").value(FACULTY_MY_COURSES))
                .andExpect(content().string(not(containsString("FACULTY_COURSE_VIEW"))));
    }

    @Test
    void facultyMemberWithThePermissionGetsTheirCourses() throws Exception {
        when(facultyCourseQueryService.getAccessibleCourses(ACCOUNT_ID)).thenReturn(List.of(
                new LmsFacultyCourseSummaryResponse(7L, "TST301", "Test Course One", new BigDecimal("4.00"), 3, "BTECH", "2026-27", "PUBLISHED"),
                new LmsFacultyCourseSummaryResponse(8L, "TST302", "Test Course Two", new BigDecimal("1.50"), 5, "BTECH", "2026-27", "DRAFT")));

        getMyCourses(facultyToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].lmsCourseId").value(7))
                .andExpect(jsonPath("$[0].courseCode").value("TST301"))
                .andExpect(jsonPath("$[0].courseName").value("Test Course One"))
                .andExpect(jsonPath("$[0].credits").value(4.00))
                .andExpect(jsonPath("$[0].semesterNumber").value(3))
                .andExpect(jsonPath("$[0].programCode").value("BTECH"))
                .andExpect(jsonPath("$[0].academicSessionCode").value("2026-27"))
                .andExpect(jsonPath("$[0].lmsCourseStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$[1].lmsCourseId").value(8))
                .andExpect(jsonPath("$[1].lmsCourseStatus").value("DRAFT"));
    }

    @Test
    void responseHoldsOnlyTheCourseSummaryFields() throws Exception {
        when(facultyCourseQueryService.getAccessibleCourses(ACCOUNT_ID)).thenReturn(List.of(
                new LmsFacultyCourseSummaryResponse(7L, "TST301", "Test Course One", new BigDecimal("4.00"), 3, "BTECH", "2026-27", "DRAFT")));

        getMyCourses(facultyToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].*", hasSize(8)))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString(LOGIN_ID))))
                .andExpect(content().string(not(containsString("accountId"))))
                .andExpect(content().string(not(containsString("facultyProfile"))))
                .andExpect(content().string(not(containsString("assignment"))))
                .andExpect(content().string(not(containsString("courseOfferingId"))))
                .andExpect(content().string(not(containsString("authorities"))));
    }

    @Test
    void facultyMemberWithNoCoursesGetsAnEmptyListNotAnError() throws Exception {
        when(facultyCourseQueryService.getAccessibleCourses(ACCOUNT_ID)).thenReturn(List.of());

        getMyCourses(facultyToken())
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void coursesAreAskedForTheAccountInTheTokenWhateverTheRequestSays() throws Exception {
        mockMvc.perform(get(FACULTY_MY_COURSES)
                        .param("accountId", "999")
                        .param("userAccountId", "999")
                        .param("facultyProfileId", "999")
                        .param("employeeId", "SOMEONE-ELSE")
                        .param("loginId", "SOMEONE-ELSE")
                        .param("teachingAssignmentId", "999")
                        .header("X-Account-Id", "999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken()))
                .andExpect(status().isOk());

        verify(facultyCourseQueryService).getAccessibleCourses(ACCOUNT_ID);
        verifyNoMoreInteractions(facultyCourseQueryService);
    }

    @Test
    void requestWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(FACULTY_MY_COURSES))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void badOrExpiredTokenIsUnauthorized() throws Exception {
        getMyCourses("not-a-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
        getMyCourses(TestTokens.expired(LOGIN_ID, ACCOUNT_ID, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void facultyMemberWithoutThePermissionIsForbidden() throws Exception {
        expectForbidden(tokenWith("ROLE_FACULTY"));
        // The student course permission is a different feature and does not stand in for it.
        expectForbidden(tokenWith("ROLE_FACULTY", "LMS_COURSE_VIEW"));

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void permissionWithoutTheFacultyRoleIsForbidden() throws Exception {
        expectForbidden(tokenWith("FACULTY_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_STUDENT", "FACULTY_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW"));

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void adminGetsNoAutomaticPass() throws Exception {
        expectForbidden(tokenWith("ROLE_ADMIN"));
        expectForbidden(tokenWith("ROLE_ADMIN", "FACULTY_COURSE_VIEW"));

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void permissionNameMustMatchExactlyWithNoPrefix() throws Exception {
        expectForbidden(tokenWith("ROLE_FACULTY", "ROLE_FACULTY_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_FACULTY", "SCOPE_FACULTY_COURSE_VIEW"));
        expectForbidden(tokenWith("ROLE_FACULTY", "faculty_course_view"));
        expectForbidden(tokenWith("FACULTY", "FACULTY_COURSE_VIEW"));

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void accountThatIsAlsoAStudentOrAdminIsStillAllowed() throws Exception {
        getMyCourses(tokenWith("ROLE_STUDENT", "ROLE_FACULTY", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW"))
                .andExpect(status().isOk());
        getMyCourses(tokenWith("ROLE_ADMIN", "ROLE_FACULTY", "FACULTY_COURSE_VIEW")).andExpect(status().isOk());

        verify(facultyCourseQueryService, times(2)).getAccessibleCourses(ACCOUNT_ID);
    }

    @Test
    void facultyPermissionOpensNoStudentRoute() throws Exception {
        for (String studentPath : List.of("/api/lms/my-courses", "/api/lms/courses/7")) {
            getAs(studentPath, facultyToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        verifyNoInteractions(studentCourseQueryService);
    }

    @Test
    void tokenFromARealLoginCarriesThePermissionThroughTheFacultyRole() throws Exception {
        Role facultyRole = new Role(RoleName.FACULTY);
        facultyRole.assignPermission(new Permission("FACULTY_COURSE_VIEW"));
        UserAccount facultyMember = new UserAccount(LOGIN_ID, "example-hash-value");
        ReflectionTestUtils.setField(facultyMember, "id", ACCOUNT_ID);
        facultyMember.assignRole(facultyRole);

        // A token issued before the role held the permission does not carry it, until the next sign-in.
        UserAccount signedInEarlier = new UserAccount("TEST-FACULTY-2", "example-hash-value");
        ReflectionTestUtils.setField(signedInEarlier, "id", 22L);
        signedInEarlier.assignRole(new Role(RoleName.FACULTY));

        getMyCourses(accessTokenService.issue(StaccUserPrincipal.from(facultyMember)).value()).andExpect(status().isOk());
        expectForbidden(accessTokenService.issue(StaccUserPrincipal.from(signedInEarlier)).value());

        verify(facultyCourseQueryService).getAccessibleCourses(ACCOUNT_ID);
        verifyNoMoreInteractions(facultyCourseQueryService);
    }

    @Test
    void routeOnlyAnswersGet() throws Exception {
        mockMvc.perform(post(FACULTY_MY_COURSES).header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(post(FACULTY_MY_COURSES)).andExpect(status().isUnauthorized());

        verifyNoInteractions(facultyCourseQueryService);
    }

    @Test
    void routeAppearsInTheApiDocumentationAsABearerTokenOperation() throws Exception {
        String operation = "$.paths['/api/lms/faculty/my-courses'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary")
                        .value("Get the authenticated faculty member's accessible LMS courses"))
                .andExpect(jsonPath(operation + ".security[0].bearerAuth").exists())
                .andExpect(jsonPath(operation + ".parameters").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/my-courses'].post").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsFacultyCourseSummaryResponse.properties.*", hasSize(8)))
                .andExpect(jsonPath("$.components.schemas.LmsFacultyCourseSummaryResponse.properties.lmsCourseStatus.enum",
                        hasSize(2)));
    }
}

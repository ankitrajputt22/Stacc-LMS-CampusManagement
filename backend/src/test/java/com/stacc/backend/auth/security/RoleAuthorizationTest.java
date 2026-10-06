package com.stacc.backend.auth.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks role rules through real requests. The "/api/test/roles/..." routes exist only in
 * test code and each one requires a role with @PreAuthorize.
 */
class RoleAuthorizationTest extends DatabaseFreeApiTest {

    private static final String STUDENT_ONLY = "/api/test/roles/student";
    private static final String FACULTY_ONLY = "/api/test/roles/faculty";
    private static final String ADMIN_ONLY = "/api/test/roles/admin";
    private static final String STUDENT_OR_FACULTY = "/api/test/roles/student-or-faculty";

    private static final String STUDENT = TestTokens.valid("2408400100011", 12, "ROLE_STUDENT");
    private static final String FACULTY = TestTokens.valid("EMP1024", 7, "ROLE_FACULTY");
    private static final String ADMIN = TestTokens.valid("ADM0001", 1, "ROLE_ADMIN");

    private ResultActions getAs(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void expectAllowed(String token, String path) throws Exception {
        getAs(token, path).andExpect(status().isOk());
    }

    private ResultActions expectForbidden(String token, String path) throws Exception {
        return getAs(token, path)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void eachRoleReachesItsOwnOperation() throws Exception {
        expectAllowed(STUDENT, STUDENT_ONLY);
        expectAllowed(FACULTY, FACULTY_ONLY);
        expectAllowed(ADMIN, ADMIN_ONLY);
    }

    @Test
    void studentCannotReachFacultyOrAdminOperations() throws Exception {
        expectForbidden(STUDENT, FACULTY_ONLY);
        expectForbidden(STUDENT, ADMIN_ONLY);
    }

    @Test
    void facultyCannotReachStudentOrAdminOperations() throws Exception {
        expectForbidden(FACULTY, STUDENT_ONLY);
        expectForbidden(FACULTY, ADMIN_ONLY);
    }

    @Test
    void operationOpenToTwoRolesAcceptsEitherOfThem() throws Exception {
        expectAllowed(STUDENT, STUDENT_OR_FACULTY);
        expectAllowed(FACULTY, STUDENT_OR_FACULTY);
    }

    @Test
    void adminGetsNoAutomaticPassToOtherRolesOperations() throws Exception {
        expectForbidden(ADMIN, STUDENT_ONLY);
        expectForbidden(ADMIN, FACULTY_ONLY);
        expectForbidden(ADMIN, STUDENT_OR_FACULTY);
    }

    @Test
    void accountWithSeveralRolesPassesEachOfTheirChecks() throws Exception {
        String studentAndFaculty = TestTokens.valid("EMP2000", 20, "ROLE_FACULTY", "ROLE_STUDENT");

        expectAllowed(studentAndFaculty, STUDENT_ONLY);
        expectAllowed(studentAndFaculty, FACULTY_ONLY);
        expectAllowed(studentAndFaculty, STUDENT_OR_FACULTY);
        expectForbidden(studentAndFaculty, ADMIN_ONLY);
    }

    @Test
    void permissionsNeverCountAsRoles() throws Exception {
        String studentWithPermissions = TestTokens.valid("2408400100011", 12, "COURSE_VIEW", "ROLE_STUDENT");
        String namesWithoutTheRolePrefix = TestTokens.valid("EMP3000", 30, "ADMIN", "COURSE_VIEW", "FACULTY");

        expectAllowed(studentWithPermissions, STUDENT_ONLY);
        expectForbidden(studentWithPermissions, FACULTY_ONLY);
        expectForbidden(namesWithoutTheRolePrefix, ADMIN_ONLY);
        expectForbidden(namesWithoutTheRolePrefix, FACULTY_ONLY);
    }

    @Test
    void signedInAccountWithoutAnyRoleIsForbiddenNotUnauthorized() throws Exception {
        String noRoles = TestTokens.valid("EMP4000", 40);

        expectForbidden(noRoles, STUDENT_ONLY);
        getAs(noRoles, "/api/test/whoami").andExpect(status().isOk());
    }

    @Test
    void missingOrBadTokenIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get(ADMIN_ONLY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
        getAs("not-a-token", ADMIN_ONLY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
    }

    @Test
    void forbiddenResponseUsesTheCommonFormatAndRevealsNothingAboutTheRule() throws Exception {
        expectForbidden(STUDENT, ADMIN_ONLY)
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.path").value(ADMIN_ONLY))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(content().string(not(containsString("hasRole"))))
                .andExpect(content().string(not(containsString("ADMIN"))))
                .andExpect(content().string(not(containsString("ROLE_"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("adminOnly"))));
    }

    @Test
    void roleChecksUseTheTokenAndNeverAskTheDatabase() throws Exception {
        expectAllowed(STUDENT, STUDENT_ONLY);
        expectForbidden(STUDENT, ADMIN_ONLY);

        verifyNoInteractions(userAccountRepository);
    }
}

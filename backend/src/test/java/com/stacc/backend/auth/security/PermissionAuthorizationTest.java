package com.stacc.backend.auth.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks permission rules through real requests. The "/api/test/permissions/..." routes and the
 * TEST_ permission names exist only in test code. They are not real Stacc permissions.
 */
class PermissionAuthorizationTest extends DatabaseFreeApiTest {

    private static final String READ = "/api/test/permissions/read";
    private static final String WRITE = "/api/test/permissions/write";
    private static final String READ_OR_SHARED = "/api/test/permissions/read-or-shared";
    private static final String FACULTY_WRITE = "/api/test/permissions/faculty-write";
    private static final String STUDENT_ONLY = "/api/test/roles/student";
    private static final String WHO_AM_I = "/api/test/whoami";

    @Autowired
    private AccessTokenService accessTokenService;

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("EMP1024", 7, authorities);
    }

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
    void accountWithThePermissionMayUseTheOperation() throws Exception {
        expectAllowed(tokenWith("TEST_READ"), READ);
    }

    @Test
    void accountWithoutThePermissionIsForbidden() throws Exception {
        expectForbidden(tokenWith(), READ);
        expectForbidden(tokenWith("TEST_OTHER"), READ);
    }

    @Test
    void permissionsStaySeparateFromEachOther() throws Exception {
        expectAllowed(tokenWith("TEST_READ"), READ);
        expectForbidden(tokenWith("TEST_READ"), WRITE);
        expectAllowed(tokenWith("TEST_WRITE"), WRITE);
        expectForbidden(tokenWith("TEST_WRITE"), READ);
    }

    @Test
    void accountMayHoldSeveralPermissions() throws Exception {
        String readAndWrite = tokenWith("TEST_READ", "TEST_WRITE");

        expectAllowed(readAndWrite, READ);
        expectAllowed(readAndWrite, WRITE);
    }

    @Test
    void repeatedPermissionInATokenChangesNothing() throws Exception {
        String repeated = tokenWith("TEST_READ", "TEST_READ", "TEST_READ");

        expectAllowed(repeated, READ);
        expectForbidden(repeated, WRITE);
    }

    @Test
    void operationOpenToSeveralPermissionsAcceptsAnyOneOfThem() throws Exception {
        expectAllowed(tokenWith("TEST_READ"), READ_OR_SHARED);
        expectAllowed(tokenWith("TEST_SHARED"), READ_OR_SHARED);
        expectAllowed(tokenWith("TEST_READ", "TEST_SHARED"), READ_OR_SHARED);
        expectForbidden(tokenWith("TEST_WRITE"), READ_OR_SHARED);
        expectForbidden(tokenWith(), READ_OR_SHARED);
    }

    @Test
    void roleAloneNeverSatisfiesAPermissionCheck() throws Exception {
        expectForbidden(tokenWith("ROLE_STUDENT"), READ);
        expectForbidden(tokenWith("ROLE_FACULTY"), READ);
    }

    @Test
    void adminGetsNoAutomaticPassToPermissions() throws Exception {
        expectForbidden(tokenWith("ROLE_ADMIN"), READ);
        expectForbidden(tokenWith("ROLE_ADMIN"), WRITE);
        expectForbidden(tokenWith("ROLE_ADMIN"), READ_OR_SHARED);
        expectAllowed(tokenWith("ROLE_ADMIN", "TEST_READ"), READ);
    }

    @Test
    void permissionAloneNeverSatisfiesARoleCheck() throws Exception {
        expectForbidden(tokenWith("TEST_READ"), STUDENT_ONLY);
        expectForbidden(tokenWith("STUDENT", "TEST_READ"), STUDENT_ONLY);
    }

    @Test
    void permissionNameIsUsedExactlyWithNoPrefix() throws Exception {
        getAs(tokenWith("ROLE_STUDENT", "TEST_READ"), WHO_AM_I)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities", hasItems("ROLE_STUDENT", "TEST_READ")))
                .andExpect(jsonPath("$.authorities", everyItem(not(startsWith("SCOPE_")))))
                .andExpect(jsonPath("$.authorities", everyItem(not(startsWith("ROLE_TEST_")))));

        // A prefixed name is a different authority and does not count as the permission.
        expectForbidden(tokenWith("ROLE_TEST_READ"), READ);
        expectForbidden(tokenWith("SCOPE_TEST_READ"), READ);
        expectForbidden(tokenWith("PERMISSION_TEST_READ"), READ);
        expectForbidden(tokenWith("test_read"), READ);
    }

    @Test
    void ruleCanRequireBothARoleAndAPermission() throws Exception {
        expectAllowed(tokenWith("ROLE_FACULTY", "TEST_WRITE"), FACULTY_WRITE);
        expectForbidden(tokenWith("ROLE_FACULTY"), FACULTY_WRITE);
        expectForbidden(tokenWith("TEST_WRITE"), FACULTY_WRITE);
        expectForbidden(tokenWith("ROLE_STUDENT", "TEST_WRITE"), FACULTY_WRITE);
        expectForbidden(tokenWith("ROLE_FACULTY", "TEST_READ"), FACULTY_WRITE);
    }

    @Test
    void permissionGivenThroughARoleReachesTheOperationInARealToken() throws Exception {
        Role faculty = new Role(RoleName.FACULTY);
        faculty.assignPermission(new Permission("TEST_READ"));
        UserAccount withPermission = new UserAccount("EMP1024", "example-hash-value");
        ReflectionTestUtils.setField(withPermission, "id", 7L);
        withPermission.assignRole(faculty);
        UserAccount withoutPermission = new UserAccount("EMP2000", "example-hash-value");
        ReflectionTestUtils.setField(withoutPermission, "id", 20L);
        withoutPermission.assignRole(new Role(RoleName.FACULTY));

        String granted = accessTokenService.issue(StaccUserPrincipal.from(withPermission)).value();
        String notGranted = accessTokenService.issue(StaccUserPrincipal.from(withoutPermission)).value();

        expectAllowed(granted, READ);
        expectForbidden(granted, WRITE);
        expectForbidden(notGranted, READ);
    }

    @Test
    void missingBadOrExpiredTokenIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get(READ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
        getAs("not-a-token", READ)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
        getAs(TestTokens.expired("EMP1024", 7, "TEST_READ"), READ)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
    }

    @Test
    void forbiddenResponseIsTheSameAsForRolesAndRevealsNothingAboutTheRule() throws Exception {
        expectForbidden(tokenWith("ROLE_STUDENT"), READ)
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.path").value(READ))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(content().string(not(containsString("TEST_READ"))))
                .andExpect(content().string(not(containsString("hasAuthority"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void permissionChecksUseTheTokenAndNeverAskTheDatabase() throws Exception {
        expectAllowed(tokenWith("TEST_READ"), READ);
        expectForbidden(tokenWith("TEST_WRITE"), READ);

        verifyNoInteractions(userAccountRepository);
    }
}

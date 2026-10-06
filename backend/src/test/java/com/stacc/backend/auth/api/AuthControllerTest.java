package com.stacc.backend.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AuthControllerTest extends DatabaseFreeApiTest {

    // Made-up values used only by these tests.
    private static final String LOGIN_ID = "2408400100011";
    private static final String PASSWORD = "TestPassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword123!";

    private static String passwordHash;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount account;

    @BeforeEach
    void setUp() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        account = new UserAccount(LOGIN_ID, passwordHash);
        ReflectionTestUtils.setField(account, "id", 12L);
        account.assignRole(new Role(RoleName.STUDENT));
        when(userAccountRepository.findWithRolesAndPermissionsByLoginId(anyString())).thenReturn(Optional.empty());
        when(userAccountRepository.findWithRolesAndPermissionsByLoginId(LOGIN_ID)).thenReturn(Optional.of(account));
    }

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String credentials(String loginId, String password) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void correctLoginIdAndPasswordReturnTheAccountAndItsRoles() throws Exception {
        login(credentials(LOGIN_ID, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(12))
                .andExpect(jsonPath("$.loginId").value(LOGIN_ID))
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
    }

    @Test
    void successfulLoginNeverReturnsPasswordData() throws Exception {
        login(credentials(LOGIN_ID, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString(passwordHash))))
                .andExpect(content().string(not(containsString("bcrypt"))));
    }

    @Test
    void accountWithSeveralRolesGetsAllOfThemInOrder() throws Exception {
        account.assignRole(new Role(RoleName.FACULTY));
        account.assignRole(new Role(RoleName.ADMIN));

        login(credentials(LOGIN_ID, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(3)))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("FACULTY"))
                .andExpect(jsonPath("$.roles[2]").value("STUDENT"));
    }

    @Test
    void roleSentByTheClientIsIgnored() throws Exception {
        login("{\"loginId\":\"" + LOGIN_ID + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
    }

    @Test
    void wrongPasswordIsUnauthorizedInTheCommonErrorFormat() throws Exception {
        login(credentials(LOGIN_ID, WRONG_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid login ID or password."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void unknownLoginIdAndDisabledAccountLookExactlyLikeAWrongPassword() throws Exception {
        String wrongPassword = messageOf(login(credentials(LOGIN_ID, WRONG_PASSWORD)).andReturn());
        String unknownLoginId = messageOf(login(credentials("EMP9999", PASSWORD)).andReturn());

        account.setStatus(AccountStatus.DISABLED);
        String disabledAccount = messageOf(login(credentials(LOGIN_ID, PASSWORD)).andReturn());

        assertEquals("401 Invalid login ID or password.", wrongPassword);
        assertEquals(wrongPassword, unknownLoginId);
        assertEquals(wrongPassword, disabledAccount);
    }

    private static String messageOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        String message = body.replaceAll(".*\"message\":\"([^\"]*)\".*", "$1");
        return result.getResponse().getStatus() + " " + message;
    }

    @Test
    void blankLoginIdIsABadRequest() throws Exception {
        login(credentials("   ", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("loginId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Login ID is required"));
    }

    @Test
    void blankPasswordIsABadRequestThatDoesNotEchoThePassword() throws Exception {
        login(credentials(LOGIN_ID, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Password is required"));
    }

    @Test
    void missingFieldsAndOverLongValuesAreBadRequests() throws Exception {
        login("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));
        login(credentials("a".repeat(UserAccount.MAX_LOGIN_ID_LENGTH + 1), PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Login ID is too long"));
        login(credentials(LOGIN_ID, "a".repeat(LoginRequest.MAX_PASSWORD_LENGTH + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Password is too long"));
        login("{\"loginId\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
    }

    @Test
    void loginOnlyAcceptsPost() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void loginDoesNotCreateASessionOrSetACookie() throws Exception {
        MvcResult result = login(credentials(LOGIN_ID, PASSWORD)).andExpect(status().isOk()).andReturn();

        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginAppearsInTheApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.summary").value("College login"))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.loginId").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.role").doesNotExist());
    }
}

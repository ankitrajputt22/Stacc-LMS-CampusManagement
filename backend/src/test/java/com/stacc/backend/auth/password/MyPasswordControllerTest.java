package com.stacc.backend.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.password.api.MyPasswordController.ChangePasswordRequest;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.token.TestTokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

class MyPasswordControllerTest extends DatabaseFreeApiTest {

    private static final String PATH = "/api/v1/me/password";
    private static final String CURRENT = "old river lamp 42";
    private static final String STUDENT_TOKEN =
            TestTokens.valid("2408400100011", 7L, "ROLE_STUDENT", "LMS_COURSE_VIEW");

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount account;

    @BeforeEach
    void anAccount() {
        account = new UserAccount("2408400100011", passwordEncoder.encode(CURRENT));
        ReflectionTestUtils.setField(account, "id", 7L);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(account));
    }

    private ResultActions change(String token, String current, String next) throws Exception {
        var request = post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    @Test
    void changesThePasswordAndSignsOtherDevicesOut() throws Exception {
        change(STUDENT_TOKEN, CURRENT, "new cocoa window 77").andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("new cocoa window 77", account.getPasswordHash())).isTrue();
        verify(sessionService).endAllExcept(7L, TestTokens.SESSION_ID, SessionEndReason.PASSWORD_CHANGED);
    }

    @Test
    void wrongCurrentOrWeakNewPasswordAnswers400WithoutEchoingIt() throws Exception {
        change(STUDENT_TOKEN, "not it at all", "new cocoa window 77").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordService.WRONG_CURRENT));
        change(STUDENT_TOKEN, CURRENT, "password123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("too easy to guess")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(containsString("password123"))));
        verify(sessionService, never()).endAllExcept(any(), any(), any());
    }

    @Test
    void needsASignedInSession() throws Exception {
        change(null, CURRENT, "new cocoa window 77").andExpect(status().isUnauthorized());
    }

    @Test
    void whileAChangeIsRequiredOnlyTheSessionPasswordAndSignOutWork() throws Exception {
        account.issueTemporaryPassword(passwordEncoder.encode(CURRENT), Instant.now().plus(1, ChronoUnit.DAYS));
        when(sessionService.check(any(), any())).thenReturn(new SessionService.SessionCheck(true, true));

        mockMvc.perform(get("/api/v1/lms/my-courses").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        change(STUDENT_TOKEN, CURRENT, "new cocoa window 77").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isNoContent());
        assertThat(account.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void requestTextFormHidesBothPasswords() {
        assertThat(new ChangePasswordRequest(CURRENT, "new cocoa window 77").toString())
                .doesNotContain(CURRENT).doesNotContain("cocoa");
    }
}

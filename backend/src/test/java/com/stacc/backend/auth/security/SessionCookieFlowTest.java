package com.stacc.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.SessionCookieAssertions;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionProperties;
import com.stacc.backend.auth.token.TestTokens;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Browsers sign in by the HttpOnly session cookie; these are the rules around it. */
class SessionCookieFlowTest extends DatabaseFreeApiTest {

    private static final String TOKEN = TestTokens.valid("student1", 7, "ROLE_STUDENT", "ROLE_FACULTY", "LMS_COURSE_VIEW");

    private static Cookie session() {
        return new Cookie(SessionProperties.COOKIE_NAME, TOKEN);
    }

    /** The XSRF token a page would read from the XSRF-TOKEN cookie Stacc sets on its answers. */
    private String xsrfToken() throws Exception {
        MvcResult answer = mockMvc.perform(get("/api/v1/auth/session").cookie(session())).andReturn();
        return answer.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';')))
                .findFirst().orElseThrow();
    }

    @Test
    void theSessionCookieSignsInAndTheCurrentSessionComesFromIt() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session").cookie(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.loginId").value("student1"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.roles[1]").value("FACULTY"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void aChangeSignedInByTheCookieMustCarryTheXsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(session())).andExpect(status().isForbidden());
        verify(sessionService, never()).end(any(), any(), any());

        String xsrf = xsrfToken();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(session(), new Cookie("XSRF-TOKEN", xsrf))
                        .header("X-XSRF-TOKEN", xsrf))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(sessionService).end(TestTokens.SESSION_ID, 7L, SessionEndReason.SIGNED_OUT);
        // The browser is told to forget the cookie at once.
        assertThat(SessionCookieAssertions.sessionCookie(result)).contains("Max-Age=0");
    }

    @Test
    void signedInAnswersNeverDeleteTheXsrfCookie() throws Exception {
        String xsrf = xsrfToken();
        MvcResult answer = mockMvc.perform(get("/api/v1/auth/session").cookie(session(), new Cookie("XSRF-TOKEN", xsrf)))
                .andExpect(status().isOk())
                .andReturn();

        // A browser keeps the last cookie it is given, so a deletion here would leave the page with no token.
        assertThat(answer.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && (cookie.contains("Max-Age=0")
                        || cookie.contains("1970")));
    }

    @Test
    void aBearerHeaderNeedsNoXsrfTokenBecauseNoSiteCanForgeIt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-everywhere").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isNoContent());

        verify(sessionService).endAll(7L, SessionEndReason.SIGNED_OUT_EVERYWHERE);
    }

    @Test
    void anEndedSessionIsRefusedWhicheverWayItsTokenArrives() throws Exception {
        when(sessionService.isActive(TestTokens.SESSION_ID, 7L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/auth/session").cookie(session())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
    }

    @Test
    void aStaleCookieNeverStopsAPersonFromSigningInAgain() throws Exception {
        when(userAccountRepository.findWithRolesAndPermissionsByLoginId(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login").cookie(new Cookie(SessionProperties.COOKIE_NAME, "expired.or.garbage"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"x\",\"password\":\"y\"}"))
                // The ordinary failed-login answer: the cookie was not even looked at.
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login ID or password."));
        verify(sessionService, never()).isActive(eq("expired.or.garbage"), any());
    }

    @Test
    void aTokenIsNeverTakenFromTheAddress() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session").param("access_token", TOKEN)).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutACookieOrHeaderAChangeIsSimplyUnauthorized() throws Exception {
        // No session cookie means nothing to forge, so the answer is the usual 401, not a CSRF 403.
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }
}

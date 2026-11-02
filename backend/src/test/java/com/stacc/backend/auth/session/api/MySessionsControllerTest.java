package com.stacc.backend.auth.session.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.UserSession;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

/** One's own sessions: listed with the current one marked, and another one ended; the current one never here. */
class MySessionsControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/v1/me/sessions";
    private static final String OTHER = "00000000-0000-4000-8000-000000000002";
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private static UserSession session(String id, String device) {
        UserSession session = new UserSession(70L, device, NOW, NOW.plusSeconds(3600));
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    @BeforeEach
    void twoOpenSessions() {
        when(sessionService.openSessions(70L)).thenReturn(List.of(session(TestTokens.SESSION_ID, "Chrome on macOS"),
                session(OTHER, "Safari on iPhone")));
    }

    private static String bearer() {
        return "Bearer " + TestTokens.valid("student1", 70);
    }

    @Test
    void theListMarksTheCurrentSession() throws Exception {
        mockMvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false))
                .andExpect(jsonPath("$[1].device").value("Safari on iPhone"));
        mockMvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherOpenSessionIsEndedButNotTheCurrentOneOrAnUnknownOne() throws Exception {
        mockMvc.perform(post(ROOT + "/" + OTHER + "/end").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        verify(sessionService).end(OTHER, 70L, SessionEndReason.SIGNED_OUT);

        mockMvc.perform(post(ROOT + "/" + TestTokens.SESSION_ID + "/end").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict());
        mockMvc.perform(post(ROOT + "/someone-elses/end").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
        verify(sessionService, never()).end(org.mockito.ArgumentMatchers.eq(TestTokens.SESSION_ID), any(), any());
        verify(sessionService, never()).end(org.mockito.ArgumentMatchers.eq("someone-elses"), any(), any());
    }
}

package com.stacc.backend.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.audit.AuditQueryService.AuditPage;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The audit log is read by admins with AUDIT_LOG_VIEW, and nothing can write, change, or delete it over HTTP. */
class AdminAuditControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/v1/admin/audit-events";

    @MockitoBean
    private AuditQueryService queries;

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("admin1", 70, authorities);
    }

    @Test
    void readingNeedsAnAdminWithTheAuditPermission() throws Exception {
        when(queries.search(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(new AuditPage(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN", "USER_VIEW")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_FACULTY", "AUDIT_LOG_VIEW")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries);

        mockMvc.perform(get(ROOT).param("action", "USER_ROLES_CHANGED").param("from", "2026-11-01T00:00:00Z")
                .param("q", "student").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN", "AUDIT_LOG_VIEW")))
                .andExpect(status().isOk());
        verify(queries).search(AuditAction.USER_ROLES_CHANGED, Instant.parse("2026-11-01T00:00:00Z"), null, "student", 0, 20);
    }

    @Test
    void thereIsNoWayToWriteOrDeleteEvents() throws Exception {
        String admin = bearer("ROLE_ADMIN", "AUDIT_LOG_VIEW");
        mockMvc.perform(post(ROOT).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().is4xxClientError());
        mockMvc.perform(delete(ROOT + "/1").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().is4xxClientError());
    }
}

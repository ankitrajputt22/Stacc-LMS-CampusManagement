package com.stacc.backend.identity.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.identity.admin.UserAdminRecords.UserDetail;
import com.stacc.backend.identity.admin.UserAdminRecords.UserPage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Admin user routes: viewing needs USER_VIEW, every change also USER_MANAGE; the admin comes from the token. */
class AdminUserControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/v1/admin/users";

    @MockitoBean
    private UserAdminService service;

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("admin1", 70, authorities);
    }

    private static UserDetail detail() {
        return new UserDetail(57L, "25CSE104", "Riya Menon", null, null, List.of("STUDENT"), "ACTIVE", true, null, null,
                null, null);
    }

    @Test
    void viewingNeedsTheAdminRoleWithUserView() throws Exception {
        when(service.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(new UserPage(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_FACULTY", "USER_VIEW")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        mockMvc.perform(get(ROOT).param("role", "STUDENT").param("status", "DISABLED").param("q", "riya")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN", "USER_VIEW"))).andExpect(status().isOk());
        verify(service).list(RoleName.STUDENT, AccountStatus.DISABLED, "riya", 0, 20);
    }

    @Test
    void changesNeedUserManageAndNeverReturnAPassword() throws Exception {
        String viewer = bearer("ROLE_ADMIN", "USER_VIEW");
        String manager = bearer("ROLE_ADMIN", "USER_VIEW", "USER_MANAGE");
        String body = "{\"loginId\":\"25CSE104\",\"fullName\":\"Riya Menon\",\"roles\":[\"ADMIN\"],"
                + "\"temporaryPassword\":\"Bright-Lantern-2047\"}";
        mockMvc.perform(post(ROOT).header(HttpHeaders.AUTHORIZATION, viewer).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post(ROOT).header(HttpHeaders.AUTHORIZATION, manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"x\",\"fullName\":\"A\",\"roles\":[],\"temporaryPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);

        when(service.create(any(), any())).thenReturn(detail());
        mockMvc.perform(post(ROOT).header(HttpHeaders.AUTHORIZATION, manager).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginId").value("25CSE104"))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(put(ROOT + "/57/status").header(HttpHeaders.AUTHORIZATION, manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}")).andExpect(status().isOk());
        verify(service).updateStatus(70L, 57L, AccountStatus.DISABLED);
        mockMvc.perform(put(ROOT + "/57/roles").header(HttpHeaders.AUTHORIZATION, manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"FACULTY\"]}")).andExpect(status().isOk());
        verify(service).updateRoles(eq(70L), eq(57L), eq(Set.of(RoleName.FACULTY)));
        mockMvc.perform(post(ROOT + "/57/temporary-password").header(HttpHeaders.AUTHORIZATION, manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"temporaryPassword\":\"Bright-Lantern-2047\"}"))
                .andExpect(status().isOk());
        verify(service).issueTemporaryPassword(70L, 57L, "Bright-Lantern-2047");
    }
}

package com.stacc.backend.academic.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Department head routes need the admin role and the department head permissions together; the actor is the token's. */
class AdminDepartmentHeadControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/v1/admin/academic";

    @MockitoBean
    private DepartmentHeadService service;

    private static String token(String... authorities) {
        return TestTokens.valid("ADM-TEST-1", 1, authorities);
    }

    private static List<MockHttpServletRequestBuilder> reads() {
        return List.of(get(ROOT + "/department-heads"), get(ROOT + "/department-heads/candidates"),
                get(ROOT + "/departments/3/heads"));
    }

    private static List<MockHttpServletRequestBuilder> changes() {
        return List.of(
                put(ROOT + "/departments/3/head").contentType(MediaType.APPLICATION_JSON).content("{\"userAccountId\":12}"),
                post(ROOT + "/departments/3/head/end"));
    }

    private void expect(String token, List<MockHttpServletRequestBuilder> requests, int status) throws Exception {
        for (MockHttpServletRequestBuilder request : requests) {
            if (token != null) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            mockMvc.perform(request).andExpect(status().is(status));
        }
    }

    @Test
    void readingNeedsTheAdminRoleAndTheViewPermission() throws Exception {
        expect(null, reads(), 401);
        expect(token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "FACULTY_ASSIGNMENT_VIEW"), reads(), 403);
        expect(token("ROLE_HOD", "DEPARTMENT_HEAD_VIEW"), reads(), 403);
        verifyNoInteractions(service);
        expect(token("ROLE_ADMIN", "DEPARTMENT_HEAD_VIEW"), reads(), 200);
    }

    @Test
    void changingAlsoNeedsTheManagePermissionAndActsAsTheTokensAccount() throws Exception {
        expect(token("ROLE_ADMIN", "DEPARTMENT_HEAD_VIEW"), changes(), 403);
        expect(token("ROLE_ADMIN", "DEPARTMENT_HEAD_MANAGE"), changes(), 403);
        expect(token("ROLE_HOD", "DEPARTMENT_HEAD_VIEW", "DEPARTMENT_HEAD_MANAGE"), changes(), 403);
        verifyNoInteractions(service);
        expect(token("ROLE_ADMIN", "DEPARTMENT_HEAD_VIEW", "DEPARTMENT_HEAD_MANAGE"), changes(), 200);
        verify(service).assign(eq(1L), eq(3L), any());
        verify(service).end(1L, 3L);
    }

    @Test
    void anAccountIsRequired() throws Exception {
        expect(token("ROLE_ADMIN", "DEPARTMENT_HEAD_VIEW", "DEPARTMENT_HEAD_MANAGE"),
                List.of(put(ROOT + "/departments/3/head").contentType(MediaType.APPLICATION_JSON).content("{}")), 400);
        verifyNoInteractions(service);
    }
}

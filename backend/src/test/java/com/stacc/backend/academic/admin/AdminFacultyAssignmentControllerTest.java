package com.stacc.backend.academic.admin;

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

/** Faculty assignment routes need the admin role and the faculty assignment permissions together. */
class AdminFacultyAssignmentControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/v1/admin/academic";

    @MockitoBean
    private FacultyAssignmentService service;

    private static String token(String... authorities) {
        return TestTokens.valid("ADM-TEST-1", 1, authorities);
    }

    private static List<MockHttpServletRequestBuilder> reads() {
        return List.of(get(ROOT + "/offerings/31/faculty"), get(ROOT + "/faculty?q=fac"));
    }

    private static List<MockHttpServletRequestBuilder> changes() {
        return List.of(
                post(ROOT + "/faculty-assignments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseOfferingId\":31,\"facultyProfileId\":11,\"role\":\"PRIMARY\"}"),
                put(ROOT + "/faculty-assignments/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PRIMARY\",\"status\":\"INACTIVE\"}"));
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
        expect(token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_MASTER_VIEW"), reads(), 403);
        expect(token("ROLE_FACULTY", "FACULTY_ASSIGNMENT_VIEW"), reads(), 403);
        verifyNoInteractions(service);
        expect(token("ROLE_ADMIN", "FACULTY_ASSIGNMENT_VIEW"), reads(), 200);
    }

    @Test
    void changingAlsoNeedsTheManagePermission() throws Exception {
        expect(token("ROLE_ADMIN", "FACULTY_ASSIGNMENT_VIEW"), changes(), 403);
        expect(token("ROLE_ADMIN", "FACULTY_ASSIGNMENT_MANAGE"), changes(), 403);
        expect(token("ROLE_STUDENT", "FACULTY_ASSIGNMENT_VIEW", "FACULTY_ASSIGNMENT_MANAGE"), changes(), 403);
        verifyNoInteractions(service);
        expect(token("ROLE_ADMIN", "FACULTY_ASSIGNMENT_VIEW", "FACULTY_ASSIGNMENT_MANAGE"), List.of(changes().get(1)), 200);
    }
}

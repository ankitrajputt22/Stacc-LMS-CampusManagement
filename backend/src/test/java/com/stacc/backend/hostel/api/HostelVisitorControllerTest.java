package com.stacc.backend.hostel.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.hostel.visitor.HostelVisitorService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Residents register, only security records at the gate, only the hostel office sets visiting hours. */
class HostelVisitorControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private HostelVisitorService service;

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("TEST-USER-1", 9L, authorities);
    }

    @Test
    void eachSideReachesOnlyItsOwnRoutes() throws Exception {
        String student = bearer("ROLE_STUDENT", "HOSTEL_VISITOR_REGISTER", "HOSTEL_VISITOR_DESK", "HOSTEL_MANAGE");
        String security = bearer("ROLE_SECURITY", "HOSTEL_VISITOR_DESK", "HOSTEL_VISITOR_REGISTER");
        mockMvc.perform(get("/api/v1/gate/visitors").header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/gate/visitors/4/arrive").header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/hostel/student/visitors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitorName\":\"Asha\",\"relation\":\"Mother\",\"expectedOn\":\"2026-11-12\"}")
                .header(HttpHeaders.AUTHORIZATION, security)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/hostel/admin/hostels/3/visiting-hours").contentType(MediaType.APPLICATION_JSON)
                .content("{\"opensAt\":\"16:00\",\"closesAt\":\"19:00\"}").header(HttpHeaders.AUTHORIZATION, security))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/gate/visitors").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_SECURITY", "GATE_PASS_VERIFY")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}

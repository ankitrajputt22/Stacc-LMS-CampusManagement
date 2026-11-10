package com.stacc.backend.hostel.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.hostel.rollcall.HostelRollCallService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only wardens with the roll call permission reach roll calls; the hostel office and students do not. */
class HostelRollCallControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private HostelRollCallService service;

    @Test
    void everyRouteNeedsTheWardenRoleAndTheRollCallPermission() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(get("/api/v1/hostel/warden/hostels/3/roll-calls"),
                post("/api/v1/hostel/warden/hostels/3/roll-calls").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/hostel/warden/roll-calls/8"),
                put("/api/v1/hostel/warden/roll-calls/8/marks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marks\":[{\"entryId\":1,\"status\":\"PRESENT\"}]}"),
                post("/api/v1/hostel/warden/roll-calls/8/finalize"));
        for (String wrong : List.of(TestTokens.valid("TEST-USER-1", 9L, "ROLE_WARDEN", "HOSTEL_WARDEN_VIEW"),
                TestTokens.valid("TEST-USER-1", 9L, "ROLE_ADMIN", "HOSTEL_MANAGE", "HOSTEL_ROLL_CALL"),
                TestTokens.valid("TEST-USER-1", 9L, "ROLE_STUDENT", "HOSTEL_ROLL_CALL"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(route.header(HttpHeaders.AUTHORIZATION, "Bearer " + wrong)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }
}

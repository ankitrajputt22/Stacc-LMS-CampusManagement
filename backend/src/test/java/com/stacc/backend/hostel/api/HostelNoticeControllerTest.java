package com.stacc.backend.hostel.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.hostel.notice.HostelNoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only the hostel office and wardens with the notice permission manage notices; residents only read their own. */
class HostelNoticeControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private HostelNoticeService service;

    @Test
    void managingNeedsAdminOrWardenWithTheNoticePermission() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(get("/api/v1/hostel/notices/manage").param("hostelId", "3"),
                post("/api/v1/hostel/notices/manage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostelId\":3,\"title\":\"Water cut\",\"message\":\"Today\",\"emergency\":true}"),
                post("/api/v1/hostel/notices/manage/5/publish"), post("/api/v1/hostel/notices/manage/5/archive"));
        for (String wrong : List.of(TestTokens.valid("TEST-USER-1", 9L, "ROLE_WARDEN", "HOSTEL_WARDEN_VIEW"),
                TestTokens.valid("TEST-USER-1", 9L, "ROLE_ADMIN", "HOSTEL_MANAGE"),
                TestTokens.valid("TEST-USER-1", 9L, "ROLE_STUDENT", "HOSTEL_NOTICE_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(route.header(HttpHeaders.AUTHORIZATION, "Bearer " + wrong)).andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(get("/api/v1/hostel/student/notices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid("TEST-USER-1", 9L, "ROLE_WARDEN", "HOSTEL_NOTICE_MANAGE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}

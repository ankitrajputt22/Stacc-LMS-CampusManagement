package com.stacc.backend.hostel.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.hostel.mess.MessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Menus are managed by the hostel office and wardens with the mess permission; residents read and rate only. */
class HostelMessControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private MessService service;

    @Test
    void managingNeedsAdminOrWardenWithTheMessPermissionAndRatingNeedsAStudent() throws Exception {
        List<MockHttpServletRequestBuilder> manage = List.of(get("/api/v1/hostel/mess/manage").param("hostelId", "3"),
                put("/api/v1/hostel/mess/manage/template").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostelId\":3,\"lines\":[{\"dayOfWeek\":1,\"meal\":\"LUNCH\",\"items\":\"Rice\"}]}"),
                put("/api/v1/hostel/mess/manage/changes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostelId\":3,\"date\":\"2026-11-12\",\"meal\":\"LUNCH\",\"items\":\"Thali\"}"));
        for (String wrong : List.of(TestTokens.valid("TEST-USER-1", 9L, "ROLE_WARDEN", "HOSTEL_WARDEN_VIEW"),
                TestTokens.valid("TEST-USER-1", 9L, "ROLE_STUDENT", "HOSTEL_MESS_MANAGE", "HOSTEL_APPLICATION_VIEW_OWN"))) {
            for (MockHttpServletRequestBuilder route : manage) {
                mockMvc.perform(route.header(HttpHeaders.AUTHORIZATION, "Bearer " + wrong)).andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(post("/api/v1/hostel/student/mess/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-11-12\",\"meal\":\"LUNCH\",\"rating\":4}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid("TEST-USER-1", 9L, "ROLE_WARDEN", "HOSTEL_MESS_MANAGE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}

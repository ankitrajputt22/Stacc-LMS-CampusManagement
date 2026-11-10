package com.stacc.backend.hostel.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.hostel.HostelRoomChangeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Residents with the room change permission ask; only the hostel office carries changes out; wardens decide elsewhere. */
class HostelRoomChangeControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private HostelRoomChangeService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", 31L, authorities);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void residentRoutesNeedTheStudentRoleAndTheRoomChangePermission() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(get("/api/v1/hostel/student/room-changes"),
                json(post("/api/v1/hostel/student/room-changes/move"), "{\"reason\":\"Too noisy\"}"),
                json(post("/api/v1/hostel/student/room-changes/swap"), "{\"partnerLoginId\":\"student1\",\"reason\":\"Friends\"}"),
                json(post("/api/v1/hostel/student/room-changes/4/answer"), "{\"accept\":true}"),
                post("/api/v1/hostel/student/room-changes/4/cancel"));
        for (String wrong : List.of(token("ROLE_STUDENT", "HOSTEL_OUTING_REQUEST"), token("ROLE_WARDEN", "HOSTEL_ROOM_CHANGE_REQUEST"),
                token("ROLE_ADMIN", "HOSTEL_MANAGE", "HOSTEL_ROOM_CHANGE_REQUEST"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void onlyTheHostelOfficeListsAndCarriesOutChanges() throws Exception {
        for (String wrong : List.of(token("ROLE_STUDENT", "HOSTEL_ROOM_CHANGE_REQUEST"), token("ROLE_WARDEN", "HOSTEL_WARDEN_VIEW", "HOSTEL_MANAGE"),
                token("ROLE_ADMIN"))) {
            mockMvc.perform(as(wrong, get("/api/v1/hostel/admin/room-changes"))).andExpect(status().isForbidden());
            mockMvc.perform(as(wrong, json(post("/api/v1/hostel/admin/room-changes/4/complete"), "{\"bedId\":7}")))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
}

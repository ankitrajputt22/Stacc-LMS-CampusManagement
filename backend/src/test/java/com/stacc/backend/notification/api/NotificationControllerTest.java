package com.stacc.backend.notification.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the Inbox routes through Spring Security, with the Inbox service as a stand-in. */
class NotificationControllerTest extends DatabaseFreeApiTest {

    private static final String ROUTE = "/api/notifications";

    @MockitoBean
    private NotificationInboxService inbox;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static List<MockHttpServletRequestBuilder> everyRoute() {
        return List.of(get(ROUTE), get(ROUTE + "/unread-count"), post(ROUTE + "/310/read"), post(ROUTE + "/310/unread"),
                post(ROUTE + "/read-all"));
    }

    @Test
    void everyRouteNeedsAKnownRoleAndTheViewPermission() throws Exception {
        for (MockHttpServletRequestBuilder request : everyRoute()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        for (String denied : new String[] {
                token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW", "ACADEMIC_CALENDAR_VIEW"),
                token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                token(1, "ROLE_ADMIN", "ADMIN_DASHBOARD_VIEW"),
                token(5, "NOTIFICATION_VIEW")}) {
            for (MockHttpServletRequestBuilder request : everyRoute()) {
                mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, denied)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(inbox);
    }

    @Test
    void studentsFacultyAndAdminsEachReachTheirOwnInbox() throws Exception {
        when(inbox.inbox(anyLong(), eq(0), eq(20), eq(false))).thenReturn(new NotificationPageResponse(List.of(), 0, 20, 0, 0));
        for (long account : new long[] {70, 80, 1}) {
            String role = account == 70 ? "ROLE_STUDENT" : account == 80 ? "ROLE_FACULTY" : "ROLE_ADMIN";
            mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION, token(account, role, "NOTIFICATION_VIEW")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.size").value(20));
            verify(inbox).inbox(account, 0, 20, false);
        }
    }

    @Test
    void theAccountComesFromTheTokenAndQueryParametersReachTheService() throws Exception {
        when(inbox.inbox(90L, 2, 5, true)).thenReturn(new NotificationPageResponse(List.of(), 2, 5, 11, 3));
        when(inbox.unreadCount(90L)).thenReturn(new UnreadNotificationCount(3));
        String multiRole = token(90, "ROLE_STUDENT", "ROLE_FACULTY", "NOTIFICATION_VIEW");

        mockMvc.perform(get(ROUTE).param("page", "2").param("size", "5").param("unreadOnly", "true")
                        .param("userAccountId", "1").header(HttpHeaders.AUTHORIZATION, multiRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get(ROUTE + "/unread-count").header(HttpHeaders.AUTHORIZATION, multiRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
        mockMvc.perform(post(ROUTE + "/read-all").header(HttpHeaders.AUTHORIZATION, multiRole)).andExpect(status().isOk());
        mockMvc.perform(post(ROUTE + "/310/read").header(HttpHeaders.AUTHORIZATION, multiRole)).andExpect(status().isOk());
        mockMvc.perform(post(ROUTE + "/310/unread").header(HttpHeaders.AUTHORIZATION, multiRole)).andExpect(status().isOk());

        verify(inbox).markAllRead(90L);
        verify(inbox).markRead(90L, 310L);
        verify(inbox).markUnread(90L, 310L);
    }

    @Test
    void anotherAccountsNotificationAnswers404() throws Exception {
        when(inbox.markRead(71L, 310L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, NotificationInboxService.NOT_FOUND));

        mockMvc.perform(post(ROUTE + "/310/read").header(HttpHeaders.AUTHORIZATION, token(71, "ROLE_STUDENT", "NOTIFICATION_VIEW")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NotificationInboxService.NOT_FOUND));
    }

    @Test
    void thereIsNoWayToCreateSendOrDeleteNotifications() throws Exception {
        String admin = token(1, "ROLE_ADMIN", "NOTIFICATION_VIEW");
        mockMvc.perform(post(ROUTE).header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hi\",\"message\":\"Hi\",\"recipientUserAccountIds\":[70]}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(ROUTE + "/310").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
        mockMvc.perform(delete(ROUTE).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(inbox);
    }

    @Test
    void theRoutesAreDocumentedWithoutCreateOrDelete() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['" + ROUTE + "/unread-count'].get").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "/{notificationRecipientId}/read'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "/{notificationRecipientId}/unread'].post").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "/read-all'].post").exists())
                .andExpect(jsonPath("$.components.schemas.NotificationInboxItem.properties.userAccountId").doesNotExist());
    }
}

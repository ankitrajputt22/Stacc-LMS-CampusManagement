package com.stacc.backend.identity.person.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.identity.person.PersonProfileResponses.MyPersonProfile;
import com.stacc.backend.identity.person.PersonProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MyPersonProfileControllerTest extends DatabaseFreeApiTest {

    private static final String ROUTE = "/api/v1/me/profile";

    @MockitoBean
    private PersonProfileService profiles;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-" + accountId, accountId, authorities);
    }

    private static MyPersonProfile aarav() {
        return new MyPersonProfile("student1", "Aarav", true, "Aarav Sharma", "Aarav", "student1@stacc.example",
                "+91 98765 43210", false);
    }

    @Test
    void readingAndChangingNeedTheirOwnPermission() throws Exception {
        mockMvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION, token(7, "ROLE_STUDENT", "LMS_COURSE_VIEW")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ROUTE).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, token(7, "ROLE_STUDENT", "PERSON_PROFILE_VIEW_OWN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void everyRoleReadsItsOwnProfileFromTheToken() throws Exception {
        when(profiles.myProfile(anyLong())).thenReturn(aarav());
        for (String role : new String[] {"ROLE_STUDENT", "ROLE_FACULTY", "ROLE_ADMIN", "ROLE_SECURITY"}) {
            mockMvc.perform(get(ROUTE).param("accountId", "1")
                            .header(HttpHeaders.AUTHORIZATION, token(7, role, "PERSON_PROFILE_VIEW_OWN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Aarav"))
                    .andExpect(jsonPath("$.fullName").value("Aarav Sharma"));
        }
        // The account came from the token, never from the accountId parameter.
        verify(profiles, org.mockito.Mockito.times(4)).myProfile(7L);
    }

    @Test
    void changingSendsOnlyThePreferredNameAndPhone() throws Exception {
        when(profiles.changeMyPersonalDetails(7L, "Aarav", "+91 98765 43210")).thenReturn(aarav());

        mockMvc.perform(put(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredName\":\"Aarav\",\"phone\":\"+91 98765 43210\",\"fullName\":\"Someone Else\"}")
                        .header(HttpHeaders.AUTHORIZATION, token(7, "ROLE_STUDENT", "PERSON_PROFILE_EDIT_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aarav Sharma"));
        verify(profiles).changeMyPersonalDetails(7L, "Aarav", "+91 98765 43210");
    }

    @Test
    void tooLongValuesAreRefusedBeforeTheService() throws Exception {
        mockMvc.perform(put(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredName\":\"" + "x".repeat(81) + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, token(7, "ROLE_STUDENT", "PERSON_PROFILE_EDIT_OWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("preferredName"));
        verify(profiles, org.mockito.Mockito.never()).changeMyPersonalDetails(anyLong(), any(), any());
    }
}

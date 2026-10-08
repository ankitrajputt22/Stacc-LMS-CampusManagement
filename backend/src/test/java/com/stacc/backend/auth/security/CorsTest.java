package com.stacc.backend.auth.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checks which web addresses a browser may call the API from. The frontend dev server is the
 * only one allowed by default. These rules never replace sign-in or permissions.
 */
class CorsTest extends DatabaseFreeApiTest {

    private static final String DEV_FRONTEND = "http://localhost:5173";
    private static final String OTHER_SITE = "http://some-other-site.example";
    private static final String LOGIN = "/api/auth/login";
    private static final String MY_COURSES = "/api/lms/my-courses";

    // What a browser sends before the real request, to ask whether it may send it.
    private ResultActions preflight(String origin, String path, String method, String requestHeaders)
            throws Exception {
        return mockMvc.perform(options(path)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders));
    }

    private static MockHttpServletRequestBuilder loginAttempt() {
        return post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"NOBODY-0000\",\"password\":\"not-a-real-password\"}");
    }

    @Test
    void devFrontendMayAskToSendALogin() throws Exception {
        preflight(DEV_FRONTEND, LOGIN, "POST", "content-type")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")));
    }

    @Test
    void devFrontendMayAskToSendATokenWithoutHavingOneYet() throws Exception {
        preflight(DEV_FRONTEND, MY_COURSES, "GET", "authorization, accept")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
    }

    @Test
    void devFrontendCanReadTheAnswerToALogin() throws Exception {
        mockMvc.perform(loginAttempt().header(HttpHeaders.ORIGIN, DEV_FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login ID or password."))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_FRONTEND));
    }

    @Test
    void beingAnAllowedOriginDoesNotReplaceSigningIn() throws Exception {
        mockMvc.perform(get(MY_COURSES).header(HttpHeaders.ORIGIN, DEV_FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_FRONTEND));

        String adminToken = TestTokens.valid("TEST-ADMIN-1", 7, "ROLE_ADMIN");
        mockMvc.perform(get(MY_COURSES)
                        .header(HttpHeaders.ORIGIN, DEV_FRONTEND)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_FRONTEND));
    }

    @Test
    void anotherSiteMayNotAskToCallTheApi() throws Exception {
        preflight(OTHER_SITE, LOGIN, "POST", "content-type")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        preflight(OTHER_SITE, MY_COURSES, "GET", "authorization")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestFromAnotherSiteIsRefusedBeforeItReachesTheLogin() throws Exception {
        mockMvc.perform(loginAttempt().header(HttpHeaders.ORIGIN, OTHER_SITE))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void lookAlikeOriginsAreNotAllowed() throws Exception {
        for (String origin : new String[] {
            "http://localhost:5174", "https://localhost:5173", "http://127.0.0.1:5173", "http://localhost:5173.example.com", "null"
        }) {
            preflight(origin, LOGIN, "POST", "content-type")
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test
    void noOriginIsEverAnsweredWithAWildcardOrWithCredentials() throws Exception {
        for (String origin : new String[] {DEV_FRONTEND, OTHER_SITE}) {
            preflight(origin, LOGIN, "POST", "content-type")
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, not("*")))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }
    }

    // The answer lists only the headers that are allowed. A browser then refuses to send the
    // request, because the other header it asked for was not granted.
    @Test
    void headersOutsideTheAllowedListAreNotGranted() throws Exception {
        preflight(DEV_FRONTEND, LOGIN, "POST", "content-type, x-account-id")
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, not(containsString("x-account-id"))));
        preflight(DEV_FRONTEND, LOGIN, "POST", "x-account-id")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void methodsOutsideTheAllowedListAreRefused() throws Exception {
        preflight(DEV_FRONTEND, LOGIN, "TRACE", "content-type")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // Outside /api no permission is given at all. Without that header a browser will not let
    // another address read the answer, whatever the status is.
    @Test
    void onlyTheApiIsOpenToTheDevFrontend() throws Exception {
        preflight(DEV_FRONTEND, "/v3/api-docs", "GET", "accept")
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.ORIGIN, DEV_FRONTEND))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestWithoutAnOriginBehavesAsBefore() throws Exception {
        mockMvc.perform(loginAttempt())
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(get(MY_COURSES)).andExpect(status().isUnauthorized());
    }

    // In Docker the browser reaches the API through the frontend's own address, and Nginx passes
    // that address on. Such a request names an origin, but it is the same one, so it is not refused.
    @Test
    void requestFromThePageItsOwnAddressIsNotTreatedAsAnotherSite() throws Exception {
        mockMvc.perform(loginAttempt()
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .with(request -> {
                            request.setServerName("localhost");
                            request.setServerPort(3000);
                            return request;
                        }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login ID or password."));

        verify(userAccountRepository).findWithRolesAndPermissionsByLoginId(any());
    }
}

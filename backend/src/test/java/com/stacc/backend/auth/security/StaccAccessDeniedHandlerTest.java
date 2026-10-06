package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class StaccAccessDeniedHandlerTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final StaccAccessDeniedHandler handler = new StaccAccessDeniedHandler(resolver);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AccessDeniedException denied = new AccessDeniedException("Access Denied");

    @Test
    void refusalIsPassedToTheSharedErrorHandling() throws Exception {
        when(resolver.resolveException(request, response, null, denied)).thenReturn(new ModelAndView());

        handler.handle(request, response, denied);

        verify(resolver).resolveException(request, response, null, denied);
        assertEquals(200, response.getStatus());
    }

    @Test
    void refusalStillBecomesForbiddenIfTheSharedErrorHandlingCannotAnswer() throws Exception {
        when(resolver.resolveException(request, response, null, denied)).thenReturn(null);

        handler.handle(request, response, denied);

        assertEquals(403, response.getStatus());
    }
}

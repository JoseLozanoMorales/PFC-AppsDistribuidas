package org.example.infrastructure.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InboundAuthorizationInterceptorTest {

    private final InboundAuthorizationInterceptor interceptor = new InboundAuthorizationInterceptor();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsBearerTokenFromInboundRequest() throws Exception {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
        MockClientHttpRequest outbound = new MockClientHttpRequest();

        interceptor.intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], 200));

        assertEquals("Bearer access-token", outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void doesNotInventAuthorizationOutsideAnHttpRequest() throws Exception {
        MockClientHttpRequest outbound = new MockClientHttpRequest();

        interceptor.intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], 200));

        assertNull(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }
}

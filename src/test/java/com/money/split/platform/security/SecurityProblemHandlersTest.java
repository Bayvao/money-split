package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.money.split.platform.api.RequestIdProvider;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies safe RFC-style problem responses emitted directly from the security filter chain.
 */
class SecurityProblemHandlersTest {

    private static final UUID REQUEST_ID = UUID.fromString("93ea231d-7260-427d-949d-4a82c4a9710f");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestIdProvider requestIdProvider = request -> {
        assertThat(request).isNotNull();
        return REQUEST_ID;
    };
    private final SecurityProblemWriter problemWriter =
            new SecurityProblemWriter(objectMapper, requestIdProvider);

    /**
     * Returns a generic 401 problem and bearer challenge without exposing decoder details.
     *
     * @throws Exception when the response cannot be written
     */
    @Test
    void writesUnauthorizedProblem() throws Exception {
        MockHttpServletRequest request = request("/api/v1/groups");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(problemWriter)
                .commence(
                        request,
                        response,
                        new BadCredentialsException("sensitive decoder detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertProblem(body, "UNAUTHORIZED", 401, "/api/v1/groups");
        assertThat(body.toString()).doesNotContain("sensitive decoder detail");
    }

    /**
     * Returns a generic 403 problem for an authenticated request without sufficient access.
     *
     * @throws Exception when the response cannot be written
     */
    @Test
    void writesAccessDeniedProblem() throws Exception {
        MockHttpServletRequest request = request("/api/v1/restricted");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(problemWriter)
                .handle(request, response, new AccessDeniedException("internal policy"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertProblem(body, "ACCESS_DENIED", 403, "/api/v1/restricted");
        assertThat(body.toString()).doesNotContain("internal policy");
    }

    /**
     * Creates an HTTP request for a protected resource.
     *
     * @param requestUri protected request URI
     * @return mock request
     */
    private static MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setRequestURI(requestUri);
        return request;
    }

    /**
     * Asserts the stable fields shared by security problems.
     *
     * @param body     parsed JSON response
     * @param code     expected stable error code
     * @param status   expected HTTP status
     * @param instance expected request URI
     */
    private static void assertProblem(
            JsonNode body, String code, int status, String instance) {
        assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("instance").asString()).isEqualTo(instance);
        assertThat(body.path("requestId").asString()).isEqualTo(REQUEST_ID.toString());
    }
}

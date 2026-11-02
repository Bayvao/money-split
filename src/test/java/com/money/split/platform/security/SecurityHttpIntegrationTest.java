package com.money.split.platform.security;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.money.split.platform.api.RequestIdFilter;
import com.money.split.support.MySqlIntegrationTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the complete servlet security boundary without contacting an identity provider.
 */
@AutoConfigureMockMvc
class SecurityHttpIntegrationTest extends MySqlIntegrationTest {

    private static final String UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Allows health and OpenAPI discovery without authentication.
     *
     * @throws Exception when request execution fails
     */
    @Test
    void allowsDocumentedPublicRoutes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, matchesPattern(UUID_PATTERN)));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, matchesPattern(UUID_PATTERN)));
    }

    /**
     * Rejects a missing token with a stable problem response and server-owned request ID.
     *
     * @throws Exception when request execution fails
     */
    @Test
    void rejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/not-implemented")
                        .queryParam("sensitive", "must-not-be-reflected")
                        .header(RequestIdFilter.HEADER_NAME, "client-controlled"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, matchesPattern("^Bearer.*")))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, matchesPattern(UUID_PATTERN)))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, not("client-controlled")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/not-implemented"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    /**
     * Rejects a raw invalid token without returning decoder details.
     *
     * @throws Exception when request execution fails
     */
    @Test
    void rejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/not-implemented")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required"))
                .andExpect(content().string(not(matchesPattern("(?s).*Raw bearer tokens.*"))));
    }

    /**
     * Allows authenticated JWT requests to reach MVC and disables CSRF for the stateless token API.
     *
     * @throws Exception when request execution fails
     */
    @Test
    void allowsAuthenticatedTokenRequestsToReachMvc() throws Exception {
        mockMvc.perform(get("/api/v1/not-implemented").with(firebaseJwt()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/not-implemented").with(firebaseJwt()))
                .andExpect(status().isNotFound());
    }

    /**
     * Protects actuator endpoints that are not on the explicit public allowlist.
     *
     * @throws Exception when request execution fails
     */
    @Test
    void protectsNonPublicActuatorRoutes() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    /**
     * Creates a Firebase-shaped authenticated principal without invoking external key services.
     *
     * @return Spring Security request post-processor for one Firebase ID token
     */
    private static JwtRequestPostProcessor firebaseJwt() {
        Instant issuedAt = Instant.parse("2026-11-02T10:00:00Z");
        return jwt().jwt(token -> token
                .header("alg", "RS256")
                .issuer("https://securetoken.google.com/money-split-test")
                .subject("firebase-uid")
                .audience(List.of("money-split-test"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("auth_time", issuedAt.minusSeconds(60).getEpochSecond())
                .claim("firebase", Map.of("sign_in_provider", "google.com")));
    }
}

package com.money.split.platform.security;

import com.money.split.platform.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.jspecify.annotations.NullMarked;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns a bearer challenge and safe problem document for missing or invalid authentication.
 */
@Component
final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
            new BearerTokenAuthenticationEntryPoint();
    private final SecurityProblemWriter problemWriter;

    /**
     * Creates the entry point with the shared problem writer.
     *
     * @param problemWriter security problem writer
     */
    ApiAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    /**
     * Adds the RFC 6750 bearer challenge and writes a generic authentication problem.
     *
     * @param request       current request
     * @param response      current response
     * @param authException internal authentication failure
     * @throws IOException when the response cannot be written
     */
    @Override
    @NullMarked
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        problemWriter.write(
                request,
                response,
                ErrorCode.UNAUTHORIZED,
                "A valid bearer token is required");
    }
}

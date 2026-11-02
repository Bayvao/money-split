package com.money.split.platform.security;

import com.money.split.platform.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.jspecify.annotations.NullMarked;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns a bearer authorization response and safe problem document for denied access.
 */
@Component
final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler =
            new BearerTokenAccessDeniedHandler();
    private final SecurityProblemWriter problemWriter;

    /**
     * Creates the handler with the shared problem writer.
     *
     * @param problemWriter security problem writer
     */
    ApiAccessDeniedHandler(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    /**
     * Applies RFC 6750 denial semantics and writes a generic authorization problem.
     *
     * @param request               current request
     * @param response              current response
     * @param accessDeniedException internal authorization failure
     * @throws IOException when the response cannot be written
     */
    @Override
    @NullMarked
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
        problemWriter.write(
                request,
                response,
                ErrorCode.ACCESS_DENIED,
                "Access to this resource is denied");
    }
}

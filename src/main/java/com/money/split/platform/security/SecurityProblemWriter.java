package com.money.split.platform.security;

import com.money.split.platform.api.ApiProblem;
import com.money.split.platform.api.ErrorCode;
import com.money.split.platform.api.RequestIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes stable API problems from filters where MVC exception handling is unavailable.
 */
@Component
final class SecurityProblemWriter {

    private final ObjectMapper objectMapper;
    private final RequestIdProvider requestIdProvider;

    /**
     * Creates the writer with the application's JSON mapper and request-ID boundary.
     *
     * @param objectMapper      configured JSON mapper
     * @param requestIdProvider request ID resolver
     */
    SecurityProblemWriter(ObjectMapper objectMapper, RequestIdProvider requestIdProvider) {
        this.objectMapper = objectMapper;
        this.requestIdProvider = requestIdProvider;
    }

    /**
     * Writes one security problem without exposing authentication or authorization internals.
     *
     * @param request   current request
     * @param response  current response
     * @param errorCode stable security error
     * @param detail    safe caller-facing detail
     * @throws IOException when the response body cannot be serialized
     */
    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            String detail)
            throws IOException {
        String code = errorCode.name();
        // Use only the request path as the instance so sensitive query parameters are never reflected.
        ApiProblem problem = new ApiProblem(
                "urn:money-split:problem:"
                        + code.toLowerCase(Locale.ROOT).replace('_', '-'),
                errorCode.title(),
                errorCode.status().value(),
                code,
                detail,
                request.getRequestURI(),
                requestIdProvider.currentRequestId(request).toString(),
                Map.of());
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

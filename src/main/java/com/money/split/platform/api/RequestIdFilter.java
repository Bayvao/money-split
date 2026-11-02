package com.money.split.platform.api;

import com.money.split.platform.time.UuidGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Creates one server-owned request ID before security or MVC handles the request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";

    private final UuidGenerator uuidGenerator;

    /**
     * Creates the filter with the application UUID boundary.
     *
     * @param uuidGenerator request ID generator
     */
    public RequestIdFilter(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * Generates a request ID, exposes it to downstream handlers, and returns it in the response.
     *
     * @param request     current request
     * @param response    current response
     * @param filterChain remaining servlet filters
     * @throws ServletException when a downstream servlet fails
     * @throws IOException      when request or response I/O fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Never trust an inbound correlation header; a client-controlled ID could spoof log linkage.
        UUID requestId = uuidGenerator.generate();
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId.toString());
        filterChain.doFilter(request, response);
    }
}

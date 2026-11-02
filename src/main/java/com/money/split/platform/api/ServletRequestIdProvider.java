package com.money.split.platform.api;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Reads request IDs from servlet request attributes.
 */
@Component
final class ServletRequestIdProvider implements RequestIdProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID currentRequestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        if (!(value instanceof UUID requestId)) {
            throw new IllegalStateException("The request ID filter did not initialize this request");
        }
        return requestId;
    }
}

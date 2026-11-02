package com.money.split.platform.api;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Resolves the server-generated correlation ID attached to an HTTP request.
 */
@FunctionalInterface
public interface RequestIdProvider {

    /**
     * Returns the request ID initialized by {@link RequestIdFilter}.
     *
     * @param request current servlet request
     * @return server-generated request ID
     * @throws IllegalStateException when the request did not pass through the request ID filter
     */
    UUID currentRequestId(HttpServletRequest request);
}

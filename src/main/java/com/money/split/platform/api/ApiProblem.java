package com.money.split.platform.api;

import java.util.Map;

/**
 * Stable error document returned to API clients.
 *
 * @param type      machine-readable problem type
 * @param title     short human-readable title
 * @param status    HTTP status code
 * @param code      stable application error code
 * @param detail    safe explanation for the caller
 * @param instance  request path that produced the problem
 * @param requestId server-generated request correlation ID
 * @param errors    field-level errors, empty when not applicable
 */
public record ApiProblem(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        String requestId,
        Map<String, String> errors) {
}

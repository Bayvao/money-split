package com.money.split.platform.api;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes currently emitted by the request-security boundary.
 */
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied");

    private final HttpStatus status;
    private final String title;

    /**
     * Associates an application code with its HTTP representation.
     *
     * @param status HTTP status
     * @param title  safe problem title
     */
    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    /**
     * Returns the HTTP status represented by this code.
     *
     * @return HTTP status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Returns the safe problem title represented by this code.
     *
     * @return problem title
     */
    public String title() {
        return title;
    }
}

package com.money.split.platform.security;

/**
 * Indicates that the current security context cannot produce a valid application actor.
 */
public final class InvalidCurrentActorException extends RuntimeException {

    /**
     * Creates an actor-validation failure with a safe message.
     *
     * @param message safe failure detail
     */
    public InvalidCurrentActorException(String message) {
        super(message);
    }
}

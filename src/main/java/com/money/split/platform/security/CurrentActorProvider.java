package com.money.split.platform.security;

/**
 * Provides the validated OIDC actor for the current security context.
 */
@FunctionalInterface
public interface CurrentActorProvider {

    /**
     * Resolves the authenticated JWT into an application actor.
     *
     * @return validated current actor
     * @throws InvalidCurrentActorException when authentication or required claims are invalid
     */
    CurrentActor currentActor();
}

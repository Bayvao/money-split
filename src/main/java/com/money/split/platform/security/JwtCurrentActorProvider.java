package com.money.split.platform.security;

import java.net.URL;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps Spring Security's authenticated Firebase ID token to the application actor contract.
 */
@Component
public final class JwtCurrentActorProvider implements CurrentActorProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new InvalidCurrentActorException("An authenticated JWT is required");
        }

        Jwt jwt = jwtAuthentication.getToken();
        URL issuer = jwt.getIssuer();
        // Prefer mutable profile names while guaranteeing the immutable Firebase UID as fallback.
        String displayName = firstNonBlank(
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getSubject());
        return new CurrentActor(
                issuer == null ? null : issuer.toString(),
                jwt.getSubject(),
                displayName,
                jwt.getClaimAsString("email"));
    }

    /**
     * Returns the first value containing non-whitespace text.
     *
     * @param values ordered candidate values
     * @return first usable value, or {@code null}
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

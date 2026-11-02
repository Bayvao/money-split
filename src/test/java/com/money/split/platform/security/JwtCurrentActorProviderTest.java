package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Verifies conversion from Spring Security authentication to the application actor boundary.
 */
class JwtCurrentActorProviderTest {

    private final JwtCurrentActorProvider provider = new JwtCurrentActorProvider();

    /**
     * Clears thread-local authentication after each test.
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Maps issuer, subject, display name, and email from a valid JWT.
     */
    @Test
    void mapsIssuerSubjectAndProfileClaims() {
        authenticate(jwt("subject-1", "Alice Smith", "alice", "alice@example.com"));

        CurrentActor actor = provider.currentActor();

        assertThat(actor.issuer()).isEqualTo("https://identity.example.com");
        assertThat(actor.subject()).isEqualTo("subject-1");
        assertThat(actor.displayName()).isEqualTo("Alice Smith");
        assertThat(actor.email()).isEqualTo("alice@example.com");
    }

    /**
     * Uses preferred username and then subject when the name claim is unavailable.
     */
    @Test
    void appliesTheDocumentedDisplayNameFallbackOrder() {
        authenticate(jwt("subject-1", null, "alice", null));
        assertThat(provider.currentActor().displayName()).isEqualTo("alice");

        authenticate(jwt("subject-2", null, null, null));
        assertThat(provider.currentActor().displayName()).isEqualTo("subject-2");
    }

    /**
     * Rejects absent, unauthenticated, and non-JWT authentication.
     */
    @Test
    void rejectsInvalidAuthenticationContext() {
        assertThatThrownBy(provider::currentActor)
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("authenticated JWT");

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt("subject", "Alice", null, null)));
        assertThatThrownBy(provider::currentActor)
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("authenticated JWT");

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", "password", List.of()));
        assertThatThrownBy(provider::currentActor)
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("authenticated JWT");
    }

    /**
     * Rejects a JWT whose required issuer claim is missing.
     */
    @Test
    void rejectsMissingIssuerClaim() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject")
                .issuedAt(Instant.parse("2026-11-02T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-02T10:05:00Z"))
                .audience(List.of("money-split-api"))
                .build();
        authenticate(token);

        assertThatThrownBy(provider::currentActor)
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
    }

    /**
     * Builds a JWT with optional profile claims.
     *
     * @param subject           subject claim
     * @param name              optional name claim
     * @param preferredUsername optional preferred username
     * @param email             optional email claim
     * @return JWT for the authentication context
     */
    private static Jwt jwt(String subject, String name, String preferredUsername, String email) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://identity.example.com")
                .subject(subject)
                .issuedAt(Instant.parse("2026-11-02T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-02T10:05:00Z"))
                .audience(List.of("money-split-api"));
        if (name != null) {
            builder.claim("name", name);
        }
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }

    /**
     * Installs an authenticated JWT with an explicit authorities collection.
     *
     * @param jwt token to authenticate
     */
    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}

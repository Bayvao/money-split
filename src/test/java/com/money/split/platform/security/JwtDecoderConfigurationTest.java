package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.money.split.platform.config.FirebaseProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies the complete claim-validator composition used by the Firebase JWT decoder.
 */
class JwtDecoderConfigurationTest {

    private static final String PROJECT_ID = "money-split-dev";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final FirebaseProperties PROPERTIES =
            new FirebaseProperties(PROJECT_ID, Set.of("google.com", "apple.com"));
    private static final OAuth2TokenValidator<Jwt> VALIDATOR =
            JwtDecoderConfiguration.jwtValidator(
                    PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    /**
     * Builds the production decoder without relying on issuer discovery.
     */
    @Test
    void buildsFirebaseDecoderFromGoogleJwkSet() {
        assertThat(new JwtDecoderConfiguration().jwtDecoder(PROPERTIES))
                .isInstanceOf(NimbusJwtDecoder.class);
    }

    /**
     * Accepts a correctly scoped Google Firebase token inside its operating window.
     */
    @Test
    void acceptsValidFirebaseToken() {
        assertThat(VALIDATOR
                .validate(jwt(ISSUER, PROJECT_ID, "firebase-uid", "google.com", -60, 60))
                .hasErrors())
                .isFalse();
    }

    /**
     * Rejects a token issued by a Firebase project other than the configured project.
     */
    @Test
    void rejectsDifferentFirebaseProjectIssuer() {
        assertThat(VALIDATOR
                .validate(jwt(
                        "https://securetoken.google.com/other-project",
                        PROJECT_ID,
                        "firebase-uid",
                        "google.com",
                        -60,
                        60))
                .hasErrors())
                .isTrue();
    }

    /**
     * Rejects expired tokens and tokens used before their not-before timestamp.
     */
    @Test
    void rejectsTokensOutsideTheirOperatingWindow() {
        assertThat(VALIDATOR
                .validate(jwt(
                        ISSUER,
                        PROJECT_ID,
                        "firebase-uid",
                        "google.com",
                        -120,
                        -61))
                .hasErrors())
                .isTrue();
        assertThat(VALIDATOR
                .validate(jwt(
                        ISSUER,
                        PROJECT_ID,
                        "firebase-uid",
                        "google.com",
                        61,
                        120))
                .hasErrors())
                .isTrue();
    }

    /**
     * Verifies that the production composition enforces project audience, Firebase UID, and provider.
     */
    @Test
    void rejectsInvalidFirebaseAudienceSubjectAndProvider() {
        assertThat(VALIDATOR
                .validate(jwt(
                        ISSUER,
                        "different-project",
                        "firebase-uid",
                        "google.com",
                        -60,
                        60))
                .hasErrors())
                .isTrue();
        assertThat(VALIDATOR
                .validate(jwt(ISSUER, PROJECT_ID, " ", "google.com", -60, 60))
                .hasErrors())
                .isTrue();
        assertThat(VALIDATOR
                .validate(jwt(ISSUER, PROJECT_ID, "firebase-uid", "password", -60, 60))
                .hasErrors())
                .isTrue();
    }

    /**
     * Builds a decoded Firebase JWT with a deterministic operating window.
     *
     * @param issuer                 token issuer
     * @param audience               Firebase project audience
     * @param subject                Firebase UID
     * @param signInProvider         Firebase sign-in provider
     * @param notBeforeOffsetSeconds not-before offset from the fixed current time
     * @param expiryOffsetSeconds    expiry offset from the fixed current time
     * @return decoded JWT for validator tests
     */
    private static Jwt jwt(
            String issuer,
            String audience,
            String subject,
            String signInProvider,
            long notBeforeOffsetSeconds,
            long expiryOffsetSeconds) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(NOW.minusSeconds(120))
                .notBefore(NOW.plusSeconds(notBeforeOffsetSeconds))
                .expiresAt(NOW.plusSeconds(expiryOffsetSeconds))
                .claim("auth_time", NOW.minusSeconds(180).getEpochSecond())
                .claim("firebase", Map.of("sign_in_provider", signInProvider))
                .build();
    }
}

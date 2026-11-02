package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.money.split.platform.config.OidcProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifies the complete claim-validator composition used by the production JWT decoder.
 */
class JwtDecoderConfigurationTest {

    private static final String ISSUER = "https://identity.example.com";
    private static final String AUDIENCE = "money-split-api";
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final OAuth2TokenValidator<Jwt> VALIDATOR =
            JwtDecoderConfiguration.jwtValidator(
                    new OidcProperties(URI.create(ISSUER), AUDIENCE),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    /**
     * Accepts a correctly scoped token inside its operating window.
     */
    @Test
    void acceptsValidIssuerAndTimestamps() {
        assertThat(VALIDATOR.validate(jwt(ISSUER, AUDIENCE, "subject", -60, 60)).hasErrors())
                .isFalse();
    }

    /**
     * Rejects a token issued by an authority other than the configured issuer.
     */
    @Test
    void rejectsDifferentIssuer() {
        assertThat(VALIDATOR
                .validate(jwt(
                        "https://untrusted.example.com",
                        AUDIENCE,
                        "subject",
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
        assertThat(VALIDATOR.validate(jwt(ISSUER, AUDIENCE, "subject", -120, -61)).hasErrors())
                .isTrue();
        assertThat(VALIDATOR.validate(jwt(ISSUER, AUDIENCE, "subject", 61, 120)).hasErrors())
                .isTrue();
    }

    /**
     * Verifies that the production composition includes the API audience and subject validators.
     */
    @Test
    void rejectsInvalidApiAudienceAndSubject() {
        assertThat(VALIDATOR
                .validate(jwt(ISSUER, "different-api", "subject", -60, 60))
                .hasErrors())
                .isTrue();
        assertThat(VALIDATOR.validate(jwt(ISSUER, AUDIENCE, " ", -60, 60)).hasErrors())
                .isTrue();
    }

    /**
     * Builds a decoded JWT with a deterministic operating window around the test clock.
     *
     * @param issuer                 token issuer
     * @param audience               token audience
     * @param subject                token subject
     * @param notBeforeOffsetSeconds not-before offset from the fixed current time
     * @param expiryOffsetSeconds    expiry offset from the fixed current time
     * @return decoded JWT for validator tests
     */
    private static Jwt jwt(
            String issuer,
            String audience,
            String subject,
            long notBeforeOffsetSeconds,
            long expiryOffsetSeconds) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .header("typ", "JWT")
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(NOW.minusSeconds(120))
                .notBefore(NOW.plusSeconds(notBeforeOffsetSeconds))
                .expiresAt(NOW.plusSeconds(expiryOffsetSeconds))
                .build();
    }
}

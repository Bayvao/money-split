package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifies exact audience validation for OIDC access tokens.
 */
class AudienceValidatorTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-11-02T10:00:00Z");
    private final AudienceValidator validator = new AudienceValidator("money-split-api");

    /**
     * Accepts a token when the configured API audience is one of its audience values.
     */
    @Test
    void acceptsTheConfiguredAudience() {
        assertThat(validator.validate(jwtWithAudience("another-api", "money-split-api")).hasErrors())
                .isFalse();
    }

    /**
     * Rejects tokens whose audience claim is absent, empty, or intended for another API.
     */
    @Test
    void rejectsMissingEmptyOrDifferentAudience() {
        assertThat(validator.validate(jwtWithAudience("another-api")).hasErrors()).isTrue();
        assertThat(validator.validate(jwtWithAudience()).hasErrors()).isTrue();
        assertThat(validator.validate(jwtWithoutAudience()).hasErrors()).isTrue();
    }

    /**
     * Rejects an empty configured audience before the validator can be used.
     */
    @Test
    void rejectsBlankConfiguredAudience() {
        assertThatThrownBy(() -> new AudienceValidator(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
    }

    /**
     * Builds a token containing the supplied audience values.
     *
     * @param audiences audience entries to add
     * @return JWT with the supplied audience claim
     */
    private static Jwt jwtWithAudience(String... audiences) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://identity.example.com")
                .subject("subject")
                .issuedAt(ISSUED_AT)
                .expiresAt(ISSUED_AT.plusSeconds(300))
                .audience(List.of(audiences))
                .build();
    }

    /**
     * Builds a token without an audience claim.
     *
     * @return JWT without an audience claim
     */
    private static Jwt jwtWithoutAudience() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://identity.example.com")
                .subject("subject")
                .issuedAt(ISSUED_AT)
                .expiresAt(ISSUED_AT.plusSeconds(300))
                .build();
    }
}

package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifies that authenticated tokens always carry a usable issuer-scoped subject.
 */
class SubjectValidatorTest {

    /**
     * Accepts a non-blank subject.
     */
    @Test
    void acceptsNonBlankSubject() {
        assertThat(new SubjectValidator().validate(jwt("subject-1")).hasErrors()).isFalse();
    }

    /**
     * Rejects absent and blank subjects before a request reaches application code.
     */
    @Test
    void rejectsMissingOrBlankSubject() {
        assertThat(new SubjectValidator().validate(jwt(null)).hasErrors()).isTrue();
        assertThat(new SubjectValidator().validate(jwt(" ")).hasErrors()).isTrue();
    }

    /**
     * Builds a token with an optional subject claim.
     *
     * @param subject subject value, or {@code null} to omit it
     * @return JWT used by the validator
     */
    private static Jwt jwt(String subject) {
        Instant issuedAt = Instant.parse("2026-11-02T10:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://identity.example.com")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}

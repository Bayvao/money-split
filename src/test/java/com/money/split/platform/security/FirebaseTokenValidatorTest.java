package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifies Firebase-specific issued-at, authentication-time, and provider claims.
 */
class FirebaseTokenValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Set<String> ALLOWED_PROVIDERS = Set.of("google.com", "apple.com");
    private final FirebaseTokenValidator validator =
            new FirebaseTokenValidator(Clock.fixed(NOW, ZoneOffset.UTC), ALLOWED_PROVIDERS);

    /**
     * Accepts Firebase tokens issued through either configured social provider.
     */
    @Test
    void acceptsGoogleAndAppleTokens() {
        assertThat(validator.validate(jwt(NOW.minusSeconds(30), NOW.minusSeconds(60), "google.com"))
                .hasErrors())
                .isFalse();
        assertThat(validator.validate(jwt(NOW.minusSeconds(30), NOW.minusSeconds(60), "apple.com"))
                .hasErrors())
                .isFalse();
    }

    /**
     * Allows future Firebase providers when deployment configuration explicitly includes them.
     */
    @Test
    void acceptsAConfiguredFutureProvider() {
        FirebaseTokenValidator configurableValidator = new FirebaseTokenValidator(
                Clock.fixed(NOW, ZoneOffset.UTC), Set.of("google.com", "apple.com", "github.com"));

        assertThat(configurableValidator
                .validate(jwt(NOW.minusSeconds(30), NOW.minusSeconds(60), "github.com"))
                .hasErrors())
                .isFalse();
    }

    /**
     * Rejects missing, malformed, and non-allowlisted Firebase provider claims.
     */
    @Test
    void rejectsInvalidSignInProvider() {
        assertThat(validator.validate(jwt(NOW.minusSeconds(30), NOW.minusSeconds(60), "password"))
                .hasErrors())
                .isTrue();
        assertThat(validator.validate(jwtWithoutFirebaseClaim()).hasErrors()).isTrue();
        assertThat(validator.validate(jwtWithFirebaseClaim(Map.of())).hasErrors()).isTrue();
        assertThat(validator.validate(jwtWithRawFirebaseClaim()).hasErrors()).isTrue();
    }

    /**
     * Rejects a missing issued-at claim or one beyond the allowed clock skew.
     */
    @Test
    void rejectsInvalidIssuedAt() {
        assertThat(validator.validate(jwt(null, NOW.minusSeconds(60), "google.com")).hasErrors())
                .isTrue();
        assertThat(validator
                .validate(jwt(NOW.plusSeconds(61), NOW.minusSeconds(60), "google.com"))
                .hasErrors())
                .isTrue();
    }

    /**
     * Rejects a missing, malformed, or future Firebase authentication time.
     */
    @Test
    void rejectsInvalidAuthenticationTime() {
        assertThat(validator.validate(jwt(NOW.minusSeconds(30), null, "google.com")).hasErrors())
                .isTrue();
        assertThat(validator
                .validate(jwt(NOW.minusSeconds(30), NOW.plusSeconds(61), "google.com"))
                .hasErrors())
                .isTrue();
        assertThat(validator
                .validate(jwtWithAuthenticationTime("not-a-timestamp"))
                .hasErrors())
                .isTrue();
        assertThat(validator
                .validate(jwtWithAuthenticationTime(
                        NOW.minusSeconds(60).getEpochSecond() + 0.5D))
                .hasErrors())
                .isTrue();
    }

    /**
     * Rejects invalid validator construction before token processing.
     */
    @Test
    void rejectsInvalidValidatorConfiguration() {
        assertThatThrownBy(() -> new FirebaseTokenValidator(null, ALLOWED_PROVIDERS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clock");
        assertThatThrownBy(() -> new FirebaseTokenValidator(Clock.systemUTC(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
        assertThatThrownBy(() -> new FirebaseTokenValidator(Clock.systemUTC(), Set.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");

        Set<String> providersWithNull = new HashSet<>();
        providersWithNull.add(null);
        assertThatThrownBy(() ->
                new FirebaseTokenValidator(Clock.systemUTC(), providersWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    /**
     * Builds a representative Firebase JWT.
     *
     * @param issuedAt           optional token issue time
     * @param authenticationTime optional Firebase authentication time
     * @param signInProvider     Firebase sign-in provider
     * @return decoded token for validation
     */
    private static Jwt jwt(
            Instant issuedAt, Instant authenticationTime, String signInProvider) {
        Jwt.Builder builder = baseJwt();
        if (issuedAt != null) {
            builder.issuedAt(issuedAt);
        }
        if (authenticationTime != null) {
            builder.claim("auth_time", authenticationTime.getEpochSecond());
        }
        return builder.claim(
                        "firebase", Map.of("sign_in_provider", signInProvider))
                .build();
    }

    /**
     * Builds a token without the required Firebase claim.
     *
     * @return decoded token missing Firebase authentication metadata
     */
    private static Jwt jwtWithoutFirebaseClaim() {
        return baseJwt().issuedAt(NOW.minusSeconds(30)).build();
    }

    /**
     * Builds a token with the supplied Firebase map.
     *
     * @param firebaseClaim Firebase metadata map
     * @return decoded token with Firebase metadata
     */
    private static Jwt jwtWithFirebaseClaim(Map<String, Object> firebaseClaim) {
        return baseJwt()
                .issuedAt(NOW.minusSeconds(30))
                .claim("auth_time", NOW.minusSeconds(60).getEpochSecond())
                .claim("firebase", firebaseClaim)
                .build();
    }

    /**
     * Builds a token whose Firebase claim has an invalid scalar type.
     *
     * @return decoded token with malformed Firebase metadata
     */
    private static Jwt jwtWithRawFirebaseClaim() {
        return baseJwt()
                .issuedAt(NOW.minusSeconds(30))
                .claim("auth_time", NOW.minusSeconds(60).getEpochSecond())
                .claim("firebase", "invalid")
                .build();
    }

    /**
     * Builds a token with a raw authentication-time claim.
     *
     * @param authenticationTime raw Firebase authentication time
     * @return decoded token with the raw authentication-time value
     */
    private static Jwt jwtWithAuthenticationTime(Object authenticationTime) {
        return baseJwt()
                .issuedAt(NOW.minusSeconds(30))
                .claim("auth_time", authenticationTime)
                .claim("firebase", Map.of("sign_in_provider", "google.com"))
                .build();
    }

    /**
     * Creates a builder containing stable Firebase identity and token-window claims.
     *
     * @return base Firebase JWT builder
     */
    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuer("https://securetoken.google.com/money-split-dev")
                .subject("firebase-uid")
                .audience(java.util.List.of("money-split-dev"))
                .expiresAt(NOW.plusSeconds(300));
    }
}

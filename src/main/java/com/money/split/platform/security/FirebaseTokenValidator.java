package com.money.split.platform.security;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/**
 * Enforces Firebase-specific token times and the configured sign-in-provider allowlist.
 */
public final class FirebaseTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final OAuth2Error INVALID_ISSUED_AT = invalidToken(
            "The Firebase token issued-at claim is not valid");
    private static final OAuth2Error INVALID_AUTH_TIME = invalidToken(
            "The Firebase authentication-time claim is not valid");
    private static final OAuth2Error INVALID_PROVIDER = invalidToken(
            "The Firebase sign-in provider is not allowed");

    private final Clock clock;
    private final Set<String> allowedSignInProviders;

    /**
     * Creates a validator with an explicit time source and provider policy.
     *
     * @param clock                  time source used for Firebase temporal claims
     * @param allowedSignInProviders accepted Firebase provider IDs
     * @throws IllegalArgumentException when the clock or provider policy is invalid
     */
    public FirebaseTokenValidator(Clock clock, Set<String> allowedSignInProviders) {
        Assert.notNull(clock, "clock is required");
        Assert.notEmpty(allowedSignInProviders, "at least one sign-in provider is required");
        for (String provider : allowedSignInProviders) {
            Assert.hasText(provider, "provider IDs must contain text");
            Assert.isTrue(
                    provider.equals(provider.strip().toLowerCase(Locale.ROOT)),
                    "provider IDs must be normalized");
        }
        this.clock = clock;
        this.allowedSignInProviders = Set.copyOf(allowedSignInProviders);
    }

    /**
     * Accepts only Firebase tokens issued and authenticated within the allowed clock skew through
     * an explicitly configured provider.
     *
     * @param token decoded Firebase ID token
     * @return validator success or an invalid-token error
     */
    @Override
    @NullMarked
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant latestAllowedTime = Instant.now(clock).plus(CLOCK_SKEW);
        Instant issuedAt = token.getIssuedAt();
        if (issuedAt == null || latestAllowedTime.isBefore(issuedAt)) {
            return OAuth2TokenValidatorResult.failure(INVALID_ISSUED_AT);
        }

        Instant authenticationTime = authenticationTime(token);
        if (authenticationTime == null || latestAllowedTime.isBefore(authenticationTime)) {
            return OAuth2TokenValidatorResult.failure(INVALID_AUTH_TIME);
        }

        String provider = signInProvider(token);
        if (provider == null || !allowedSignInProviders.contains(provider)) {
            return OAuth2TokenValidatorResult.failure(INVALID_PROVIDER);
        }
        return OAuth2TokenValidatorResult.success();
    }

    /**
     * Reads Firebase's numeric-date authentication time without accepting coercible text.
     *
     * @param token decoded Firebase ID token
     * @return authentication time, or {@code null} when missing or malformed
     */
    private static Instant authenticationTime(Jwt token) {
        Object value = token.getClaim("auth_time");
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            Long epochSecond = exactEpochSecond(number);
            if (epochSecond == null) {
                return null;
            }
            try {
                return Instant.ofEpochSecond(epochSecond);
            } catch (DateTimeException exception) {
                return null;
            }
        }
        return null;
    }

    /**
     * Converts only integral JSON numeric representations without truncating fractions or overflow.
     *
     * @param number raw numeric-date value
     * @return exact epoch second, or {@code null} for an unsupported or lossy conversion
     */
    private static Long exactEpochSecond(Number number) {
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Reads the provider selected by Firebase for the current sign-in operation.
     *
     * @param token decoded Firebase ID token
     * @return exact Firebase provider ID, or {@code null} when missing or malformed
     */
    private static String signInProvider(Jwt token) {
        Object firebaseClaim = token.getClaim("firebase");
        if (!(firebaseClaim instanceof Map<?, ?> firebase)) {
            return null;
        }
        Object provider = firebase.get("sign_in_provider");
        return provider instanceof String providerId ? providerId : null;
    }

    /**
     * Creates one Firebase invalid-token error.
     *
     * @param description safe validator description
     * @return OAuth2 invalid-token error
     */
    private static OAuth2Error invalidToken(String description) {
        return new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                description,
                "https://firebase.google.com/docs/auth/admin/verify-id-tokens");
    }
}

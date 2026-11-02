package com.money.split.platform.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validated Firebase identity and mutable profile claims for the current request.
 *
 * @param issuer      exact Firebase issuer
 * @param subject     issuer-scoped immutable Firebase UID
 * @param displayName normalized display name
 * @param email       optional normalized email profile claim
 */
public record CurrentActor(String issuer, String subject, String displayName, String email) {

    private static final int MAX_ISSUER_LENGTH = 255;
    private static final int MAX_FIREBASE_UID_LENGTH = 128;
    private static final int MAX_DISPLAY_NAME_LENGTH = 120;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /**
     * Validates and normalizes claims before they cross into application code.
     *
     * @param issuer      exact Firebase issuer
     * @param subject     issuer-scoped immutable Firebase UID
     * @param displayName display name selected from the documented claim fallback
     * @param email       optional email profile claim
     */
    public CurrentActor {
        issuer = validateIssuer(issuer);
        subject = validateFirebaseUid(subject);
        displayName = requiredValue(displayName, "display name", MAX_DISPLAY_NAME_LENGTH);
        email = validateEmail(email);
    }

    /**
     * Validates an absolute HTTP(S) issuer URI and preserves its exact normalized text.
     *
     * @param value issuer claim
     * @return normalized issuer
     * @throws InvalidCurrentActorException when the issuer is missing or malformed
     */
    private static String validateIssuer(String value) {
        String issuer = requiredValue(value, "issuer", MAX_ISSUER_LENGTH);
        try {
            URI uri = new URI(issuer);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute()
                    || (!scheme.equals("https") && !scheme.equals("http"))
                    || uri.getHost() == null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new InvalidCurrentActorException(
                        "Identity issuer must be an absolute HTTP(S) URI without query or fragment");
            }
            return issuer;
        } catch (URISyntaxException exception) {
            throw new InvalidCurrentActorException("Identity issuer is invalid");
        }
    }

    /**
     * Normalizes and bounds a required string claim.
     *
     * @param value     claim value
     * @param field     safe field label used in errors
     * @param maxLength maximum accepted length
     * @return normalized claim value
     * @throws InvalidCurrentActorException when the value is absent or too long
     */
    private static String requiredValue(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidCurrentActorException("Identity " + field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InvalidCurrentActorException("Identity " + field + " is too long");
        }
        return normalized;
    }

    /**
     * Validates the immutable Firebase UID without changing its exact representation.
     *
     * @param value Firebase UID
     * @return exact Firebase UID
     * @throws InvalidCurrentActorException when the value is absent, padded, or too long
     */
    private static String validateFirebaseUid(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCurrentActorException("Identity Firebase UID is required");
        }
        if (!value.equals(value.strip()) || value.length() > MAX_FIREBASE_UID_LENGTH) {
            throw new InvalidCurrentActorException("Identity Firebase UID is invalid");
        }
        return value;
    }

    /**
     * Normalizes an optional email claim and rejects unsafe or oversized values.
     *
     * @param value optional email claim
     * @return normalized email or {@code null}
     * @throws InvalidCurrentActorException when the email is malformed or too long
     */
    private static String validateEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_EMAIL_LENGTH
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidCurrentActorException("Identity email is invalid");
        }
        return normalized;
    }
}

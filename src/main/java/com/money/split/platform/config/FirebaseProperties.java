package com.money.split.platform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Required trust settings for Firebase Authentication ID tokens.
 *
 * @param projectId              trusted Firebase project ID
 * @param allowedSignInProviders Firebase provider IDs accepted by this API
 */
@Validated
@ConfigurationProperties("app.security.firebase")
public record FirebaseProperties(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]{4,28}[a-z0-9]$")
        String projectId,
        @NotNull @NotEmpty Set<@NotBlank @Size(max = 100) String> allowedSignInProviders) {

    private static final String ISSUER_PREFIX = "https://securetoken.google.com/";

    /**
     * Normalizes configuration values before Bean Validation checks the bound object.
     *
     * @param projectId              trusted Firebase project ID
     * @param allowedSignInProviders Firebase provider IDs accepted by this API
     */
    public FirebaseProperties {
        if (projectId != null) {
            projectId = projectId.strip();
        }
        if (allowedSignInProviders != null) {
            Set<String> normalizedProviders = new LinkedHashSet<>();
            for (String provider : allowedSignInProviders) {
                normalizedProviders.add(provider == null
                        ? null
                        : provider.strip().toLowerCase(Locale.ROOT));
            }
            allowedSignInProviders = Collections.unmodifiableSet(normalizedProviders);
        }
    }

    /**
     * Returns the exact issuer required in Firebase ID tokens for this project.
     *
     * @return trusted Firebase issuer
     */
    public String issuer() {
        return ISSUER_PREFIX + projectId;
    }

    /**
     * Returns the exact audience required in Firebase ID tokens.
     *
     * @return trusted Firebase project audience
     */
    public String audience() {
        return projectId;
    }
}

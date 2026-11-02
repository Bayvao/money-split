package com.money.split.platform.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Required OIDC resource-server settings.
 *
 * @param issuerUri exact trusted issuer URI
 * @param audience  audience required in access tokens
 */
@Validated
@ConfigurationProperties("app.security.oidc")
public record OidcProperties(
        @NotNull URI issuerUri,
        @NotBlank @Size(max = 255) String audience) {

    /**
     * Normalizes surrounding audience whitespace after configuration binding.
     *
     * @param issuerUri exact trusted issuer URI
     * @param audience  required access-token audience
     */
    public OidcProperties {
        if (audience != null) {
            audience = audience.strip();
        }
    }

    /**
     * Ensures the issuer is an absolute HTTP(S) URL without query or fragment components.
     *
     * @return {@code true} when the issuer can identify an OIDC authority
     */
    @AssertTrue(message = "issuer URI must be an absolute HTTP(S) URL without query or fragment")
    @SuppressWarnings("unused")
    public boolean isIssuerUriValid() {
        if (issuerUri == null) {
            return true;
        }
        String scheme = issuerUri.getScheme();
        String normalizedScheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        return issuerUri.isAbsolute()
                && ("https".equals(normalizedScheme) || "http".equals(normalizedScheme))
                && issuerUri.getHost() != null
                && issuerUri.getQuery() == null
                && issuerUri.getFragment() == null;
    }
}

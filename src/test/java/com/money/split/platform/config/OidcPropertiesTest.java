package com.money.split.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies startup validation for OIDC issuer and audience configuration.
 */
class OidcPropertiesTest {

    /**
     * Accepts absolute HTTP and HTTPS issuers with a non-blank audience.
     */
    @Test
    void acceptsValidOidcConfiguration() {
        OidcProperties properties =
                new OidcProperties(URI.create("https://identity.example.com"), " money-split-api ");

        assertThat(violations(properties)).isEmpty();
        assertThat(properties.audience()).isEqualTo("money-split-api");
        assertThat(violations(
                new OidcProperties(URI.create("http://localhost:8080"), "local-api")))
                .isEmpty();
    }

    /**
     * Rejects missing, relative, or unsupported issuer URIs and a blank audience.
     */
    @Test
    void rejectsInvalidOidcConfiguration() {
        assertThat(violations(new OidcProperties(null, "money-split-api"))).isNotEmpty();
        assertThat(violations(new OidcProperties(URI.create("/relative"), "money-split-api")))
                .isNotEmpty();
        assertThat(violations(new OidcProperties(
                URI.create("ftp://identity.example.com"), "money-split-api")))
                .isNotEmpty();
        assertThat(violations(
                new OidcProperties(URI.create("https:/missing-host"), "money-split-api")))
                .isNotEmpty();
        assertThat(violations(new OidcProperties(
                URI.create("https://identity.example.com?tenant=unsafe"),
                "money-split-api")))
                .isNotEmpty();
        assertThat(violations(new OidcProperties(
                URI.create("https://identity.example.com#unsafe"),
                "money-split-api")))
                .isNotEmpty();
        assertThat(violations(
                new OidcProperties(URI.create("https://identity.example.com"), " ")))
                .isNotEmpty();
        assertThat(violations(new OidcProperties(
                URI.create("https://identity.example.com"), "a".repeat(256))))
                .isNotEmpty();
    }

    /**
     * Validates one property object with Jakarta Bean Validation.
     *
     * @param properties properties to validate
     * @return constraint violations
     */
    private static Set<ConstraintViolation<OidcProperties>> violations(
            OidcProperties properties) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(properties);
        }
    }
}

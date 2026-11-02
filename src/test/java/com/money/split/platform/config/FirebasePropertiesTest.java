package com.money.split.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies startup validation and derived trust settings for Firebase Authentication.
 */
class FirebasePropertiesTest {

    /**
     * Normalizes a valid project ID and provider allowlist and derives Firebase token values.
     */
    @Test
    void acceptsAndNormalizesValidFirebaseConfiguration() {
        FirebaseProperties properties = new FirebaseProperties(
                " money-split-dev ", Set.of(" google.com ", "APPLE.COM"));

        assertThat(violations(properties)).isEmpty();
        assertThat(properties.projectId()).isEqualTo("money-split-dev");
        assertThat(properties.allowedSignInProviders())
                .containsExactlyInAnyOrder("google.com", "apple.com");
        assertThat(properties.issuer())
                .isEqualTo("https://securetoken.google.com/money-split-dev");
        assertThat(properties.audience()).isEqualTo("money-split-dev");
    }

    /**
     * Prevents caller mutations through either the source set or the record accessor.
     */
    @Test
    void defensivelyCopiesTheProviderAllowlist() {
        Set<String> providers = new HashSet<>(Set.of("google.com"));
        FirebaseProperties properties = new FirebaseProperties("money-split", providers);

        providers.add("password");

        assertThat(properties.allowedSignInProviders()).containsExactly("google.com");
        assertThatThrownBy(() -> properties.allowedSignInProviders().add("apple.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Rejects missing or malformed Firebase project IDs.
     */
    @Test
    void rejectsInvalidProjectIds() {
        Set<String> providers = Set.of("google.com");

        assertThat(violations(new FirebaseProperties(null, providers))).isNotEmpty();
        assertThat(violations(new FirebaseProperties(" ", providers))).isNotEmpty();
        assertThat(violations(new FirebaseProperties("short", providers))).isNotEmpty();
        assertThat(violations(new FirebaseProperties("Money-Split", providers))).isNotEmpty();
        assertThat(violations(new FirebaseProperties("money-split-", providers))).isNotEmpty();
        assertThat(violations(new FirebaseProperties("a".repeat(31), providers))).isNotEmpty();
    }

    /**
     * Rejects an absent, empty, blank, or oversized provider allowlist entry.
     */
    @Test
    void rejectsInvalidProviderAllowlists() {
        assertThat(violations(new FirebaseProperties("money-split", null))).isNotEmpty();
        assertThat(violations(new FirebaseProperties("money-split", Set.of()))).isNotEmpty();
        assertThat(violations(new FirebaseProperties("money-split", Set.of(" ")))).isNotEmpty();
        assertThat(violations(
                new FirebaseProperties("money-split", Set.of("p".repeat(101)))))
                .isNotEmpty();

        Set<String> providersWithNull = new HashSet<>();
        providersWithNull.add(null);
        assertThat(violations(new FirebaseProperties("money-split", providersWithNull)))
                .isNotEmpty();
    }

    /**
     * Validates one Firebase property object with Jakarta Bean Validation.
     *
     * @param properties properties to validate
     * @return constraint violations
     */
    private static Set<ConstraintViolation<FirebaseProperties>> violations(
            FirebaseProperties properties) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(properties);
        }
    }
}

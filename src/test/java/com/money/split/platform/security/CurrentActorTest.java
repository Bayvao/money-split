package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Verifies normalization and validation of the authenticated OIDC actor.
 */
class CurrentActorTest {

    /**
     * Preserves the issuer-scoped identity while normalizing surrounding profile whitespace.
     */
    @Test
    void acceptsAndNormalizesAValidActor() {
        CurrentActor actor = new CurrentActor(
                " https://identity.example.com ",
                " provider-subject ",
                " Alice ",
                " alice@example.com ");

        assertThat(actor.issuer()).isEqualTo("https://identity.example.com");
        assertThat(actor.subject()).isEqualTo("provider-subject");
        assertThat(actor.displayName()).isEqualTo("Alice");
        assertThat(actor.email()).isEqualTo("alice@example.com");
    }

    /**
     * Treats a blank email as absent because email is optional profile data, not identity.
     */
    @Test
    void acceptsAnActorWithoutEmail() {
        CurrentActor actor =
                new CurrentActor("http://localhost:8080/issuer", "subject", "Alice", " ");

        assertThat(actor.email()).isNull();
    }

    /**
     * Rejects malformed, relative, and non-HTTP issuer values.
     */
    @Test
    void rejectsInvalidIssuer() {
        assertThatThrownBy(() -> constructActor(null, "subject", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> constructActor("not a uri", "subject", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> constructActor("/relative", "subject", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(
                () -> constructActor(
                        "ftp://identity.example.com", "subject", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() ->
                constructActor(
                        "https://identity.example.com?tenant=unsafe",
                        "subject",
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() ->
                constructActor(
                        "https://identity.example.com#unsafe",
                        "subject",
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> constructActor(
                "https://" + "a".repeat(244) + ".com", "subject", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
    }

    /**
     * Rejects missing or oversized identity and display claims.
     */
    @Test
    void rejectsInvalidRequiredClaims() {
        assertThatThrownBy(
                () -> constructActor(
                        "https://identity.example.com", null, "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(
                () -> constructActor(
                        "https://identity.example.com", " ", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(() ->
                constructActor(
                        "https://identity.example.com",
                        "s".repeat(256),
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(
                () -> constructActor(
                        "https://identity.example.com", "subject", " ", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("display name");
        assertThatThrownBy(() ->
                constructActor(
                        "https://identity.example.com",
                        "subject",
                        "a".repeat(121),
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("display name");
    }

    /**
     * Rejects malformed and oversized optional email claims.
     */
    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() ->
                constructActor(
                        "https://identity.example.com",
                        "subject",
                        "Alice",
                        "not-an-email"))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("email");
        assertThatThrownBy(() ->
                constructActor(
                        "https://identity.example.com",
                        "subject",
                        "Alice",
                        "a".repeat(310) + "@example.com"))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("email");
    }

    /**
     * Constructs an actor for validation assertions.
     *
     * @param issuer      OIDC issuer
     * @param subject     issuer-scoped subject
     * @param displayName display name
     * @param email       optional email
     */
    private static void constructActor(
            String issuer, String subject, String displayName, String email) {
        assertThat(new CurrentActor(issuer, subject, displayName, email)).isNotNull();
    }
}

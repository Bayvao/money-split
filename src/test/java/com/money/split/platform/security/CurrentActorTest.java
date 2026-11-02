package com.money.split.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Verifies normalization and validation of the authenticated Firebase actor.
 */
class CurrentActorTest {

    /**
     * Preserves the issuer-scoped identity while normalizing surrounding profile whitespace.
     */
    @Test
    void acceptsAndNormalizesAValidActor() {
        CurrentActor actor = new CurrentActor(
                " https://securetoken.google.com/money-split-dev ",
                "firebase-uid",
                " Alice ",
                " alice@example.com ");

        assertThat(actor.issuer()).isEqualTo("https://securetoken.google.com/money-split-dev");
        assertThat(actor.subject()).isEqualTo("firebase-uid");
        assertThat(actor.displayName()).isEqualTo("Alice");
        assertThat(actor.email()).isEqualTo("alice@example.com");
    }

    /**
     * Treats a blank email as absent because email is optional profile data, not identity.
     */
    @Test
    void acceptsAnActorWithoutEmail() {
        CurrentActor actor = new CurrentActor(
                "https://securetoken.google.com/money-split-dev",
                "firebase-uid",
                "Alice",
                " ");

        assertThat(actor.email()).isNull();
    }

    /**
     * Accepts Apple's private relay address as optional mutable profile data.
     */
    @Test
    void acceptsApplePrivateRelayEmail() {
        CurrentActor actor = new CurrentActor(
                "https://securetoken.google.com/money-split-dev",
                "firebase-uid",
                "Alice",
                "random@privaterelay.appleid.com");

        assertThat(actor.email()).isEqualTo("random@privaterelay.appleid.com");
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
                        "ftp://securetoken.google.com", "firebase-uid", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() ->
                constructActor(
                        "https://securetoken.google.com?tenant=unsafe",
                        "firebase-uid",
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() ->
                constructActor(
                        "https://securetoken.google.com#unsafe",
                        "firebase-uid",
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> constructActor(
                "https://" + "a".repeat(244) + ".com", "firebase-uid", "Alice", null))
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
                        "https://securetoken.google.com/money-split-dev", null, "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("Firebase UID");
        assertThatThrownBy(
                () -> constructActor(
                        "https://securetoken.google.com/money-split-dev", " ", "Alice", null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("Firebase UID");
        assertThatThrownBy(() ->
                constructActor(
                        "https://securetoken.google.com/money-split-dev",
                        "u".repeat(129),
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("Firebase UID");
        assertThatThrownBy(() ->
                constructActor(
                        "https://securetoken.google.com/money-split-dev",
                        " padded",
                        "Alice",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("Firebase UID");
        assertThatThrownBy(
                () -> constructActor(
                        "https://securetoken.google.com/money-split-dev",
                        "firebase-uid",
                        " ",
                        null))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("display name");
        assertThatThrownBy(() ->
                constructActor(
                        "https://securetoken.google.com/money-split-dev",
                        "firebase-uid",
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
                        "https://securetoken.google.com/money-split-dev",
                        "firebase-uid",
                        "Alice",
                        "not-an-email"))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("email");
        assertThatThrownBy(() ->
                constructActor(
                        "https://securetoken.google.com/money-split-dev",
                        "firebase-uid",
                        "Alice",
                        "a".repeat(310) + "@example.com"))
                .isInstanceOf(InvalidCurrentActorException.class)
                .hasMessageContaining("email");
    }

    /**
     * Constructs an actor for validation assertions.
     *
     * @param issuer      Firebase issuer
     * @param subject     Firebase UID
     * @param displayName display name
     * @param email       optional email
     */
    private static void constructActor(
            String issuer, String subject, String displayName, String email) {
        assertThat(new CurrentActor(issuer, subject, displayName, email)).isNotNull();
    }
}

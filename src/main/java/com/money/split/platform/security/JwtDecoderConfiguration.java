package com.money.split.platform.security;

import com.money.split.platform.config.FirebaseProperties;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the production JWT decoder for Firebase Authentication ID tokens.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FirebaseProperties.class)
@Profile("!test")
public class JwtDecoderConfiguration {

    static final String FIREBASE_JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/"
                    + "securetoken@system.gserviceaccount.com";

    /**
     * Creates a decoder restricted to Firebase's Google-hosted RS256 signing keys.
     *
     * @param properties validated Firebase trust configuration
     * @return production JWT decoder
     */
    @Bean
    JwtDecoder jwtDecoder(FirebaseProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(FIREBASE_JWK_SET_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .validateType(true)
                .build();
        decoder.setJwtValidator(jwtValidator(properties, Clock.systemUTC()));
        return decoder;
    }

    /**
     * Composes standard token-type, timestamp, certificate-thumbprint, issuer, project audience,
     * Firebase UID, authentication-time, and provider checks.
     *
     * @param properties validated Firebase trust configuration
     * @param clock      clock used to evaluate the token operating window
     * @return complete validator installed on the production decoder
     */
    static OAuth2TokenValidator<Jwt> jwtValidator(
            FirebaseProperties properties, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        // Supplying the clock explicitly keeps timestamp behavior deterministic in focused tests.
        OAuth2TokenValidator<Jwt> standardValidators =
                JwtValidators.createDefaultWithValidators(List.of(
                        timestampValidator,
                        new JwtIssuerValidator(properties.issuer())));
        return new DelegatingOAuth2TokenValidator<>(
                standardValidators,
                new AudienceValidator(properties.audience()),
                new SubjectValidator(),
                new FirebaseTokenValidator(clock, properties.allowedSignInProviders()));
    }
}

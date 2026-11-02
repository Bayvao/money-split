package com.money.split.platform.security;

import com.money.split.platform.config.OidcProperties;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the production JWT decoder from validated OIDC configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OidcProperties.class)
@Profile("!test")
public class JwtDecoderConfiguration {

    /**
     * Creates a decoder that verifies signatures and validates timestamps, issuer, audience, and subject.
     *
     * @param properties validated OIDC configuration
     * @return production JWT decoder
     */
    @Bean
    JwtDecoder jwtDecoder(OidcProperties properties) {
        String issuer = properties.issuerUri().toString();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(jwtValidator(properties, Clock.systemUTC()));
        return decoder;
    }

    /**
     * Composes standard token-type, timestamp, certificate-thumbprint, and issuer checks with
     * application audience and identity requirements.
     *
     * @param properties validated OIDC configuration
     * @param clock      clock used to evaluate the token operating window
     * @return complete validator installed on the production decoder
     */
    static OAuth2TokenValidator<Jwt> jwtValidator(OidcProperties properties, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        // Supplying the clock explicitly keeps timestamp behavior deterministic in focused tests.
        OAuth2TokenValidator<Jwt> standardValidators =
                JwtValidators.createDefaultWithValidators(List.of(
                        timestampValidator,
                        new JwtIssuerValidator(properties.issuerUri().toString())));
        return new DelegatingOAuth2TokenValidator<>(
                standardValidators,
                new AudienceValidator(properties.audience()),
                new SubjectValidator());
    }
}

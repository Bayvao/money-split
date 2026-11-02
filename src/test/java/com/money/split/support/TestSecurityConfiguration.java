package com.money.split.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Supplies a deterministic JWT decoder so integration tests never contact an external issuer.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    /**
     * Rejects raw bearer tokens while allowing Spring Security's JWT test post-processor to
     * install authenticated principals directly.
     *
     * @return a decoder that rejects every raw token
     */
    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        return _ -> {
            throw new BadJwtException("Raw bearer tokens are not accepted by the test decoder");
        };
    }
}

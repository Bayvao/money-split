package com.money.split.platform.security;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/**
 * Requires the configured API identifier in a token's audience claim.
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The token audience is not valid",
            "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.3");

    private final String audience;

    /**
     * Creates an exact audience validator.
     *
     * @param audience audience required by this API
     * @throws IllegalArgumentException when the audience is blank
     */
    public AudienceValidator(String audience) {
        Assert.hasText(audience, "audience is required");
        this.audience = audience;
    }

    /**
     * Accepts a JWT only when its audience collection contains the configured value.
     *
     * @param token decoded JWT
     * @return validator success or an invalid-token error
     */
    @Override
    @NullMarked
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}

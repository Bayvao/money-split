package com.money.split.platform.security;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Requires the issuer-scoped subject used as the durable external identity.
 */
public final class SubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SUBJECT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The token subject is not valid",
            "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2");

    /**
     * Accepts a JWT only when its subject contains non-whitespace text.
     *
     * @param token decoded JWT
     * @return validator success or an invalid-token error
     */
    @Override
    @NullMarked
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (StringUtils.hasText(token.getSubject())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
    }
}

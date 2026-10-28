package com.eatsnow.identity.domain.port.out;

import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.model.Profile;

/**
 * Outbound port for signing and issuing JWT access tokens and exposing public JWKS (Spec 3.1 & ADR 005).
 */
public interface TokenIssuerPort {

    IssuedToken issueToken(Profile profile);

    String getPublicJwksJson();
}

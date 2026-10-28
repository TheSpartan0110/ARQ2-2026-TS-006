package com.eatsnow.identity.domain.port.in;

import com.eatsnow.identity.domain.model.IssuedToken;

/**
 * Inbound port for authenticating and obtaining an access token (Spec 3.1 & ADR 005).
 */
public interface AuthenticateUseCase {
    IssuedToken authenticate(String email, String password);
}

package com.eatsnow.identity.domain.port.in;

/**
 * Inbound port for the activation flow of accounts created by an ADMIN (Spec 3.2): the person follows a one-time link
 * and sets their password.
 */
public interface ActivateAccountUseCase {

    /** True when the token exists, is unused and has not expired. */
    boolean isUsable(String rawToken);

    /** Sets the password and consumes the token; fails with InvalidRequestException when the token is not usable. */
    void activate(String rawToken, String rawPassword);
}

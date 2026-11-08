package com.eatsnow.identity.domain.port.out;

/**
 * Outbound port that revokes every authorization (refresh tokens included) issued to a user (Spec 3.2).
 */
public interface TokenRevocationPort {
    void revokeAll(String email);
}

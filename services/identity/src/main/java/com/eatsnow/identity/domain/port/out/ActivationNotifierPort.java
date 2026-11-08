package com.eatsnow.identity.domain.port.out;

import com.eatsnow.identity.domain.model.Profile;

/**
 * Outbound port that delivers the activation link to the person whose account an ADMIN created (Spec 3.2).
 */
public interface ActivationNotifierPort {
    void sendActivationLink(Profile profile, String rawToken);
}

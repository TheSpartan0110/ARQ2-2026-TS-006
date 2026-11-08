package com.eatsnow.identity.domain.port.in;

import com.eatsnow.identity.domain.model.Profile;

/**
 * Inbound port for customer self-registration on the Identity pages (Spec 3.1 & ADR 005). Grants only the USER role.
 */
public interface RegisterCustomerUseCase {
    Profile register(String displayName, String email, String rawPassword);
}

package com.eatsnow.notifications.domain.port.out;

import com.eatsnow.notifications.domain.model.CustomerContact;
import java.util.UUID;

/** Internal Identity endpoint GET /internal/v1/profiles/{profileId}/contact, 2-second timeout (Spec 3.7). */
public interface ContactLookupPort {
    /**
     * @throws com.eatsnow.notifications.domain.exception.ContactNotFoundException       404, permanent
     * @throws com.eatsnow.notifications.domain.exception.TransientNotificationException timeout or server error
     */
    CustomerContact find(UUID profileId);
}

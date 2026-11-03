package com.eatsnow.notifications.domain.model;

import java.util.UUID;

/** Contact data resolved for ONE send attempt: never cached beyond it and never logged (Spec 3.7). */
public record CustomerContact(UUID profileId, String displayName, String email) {
}

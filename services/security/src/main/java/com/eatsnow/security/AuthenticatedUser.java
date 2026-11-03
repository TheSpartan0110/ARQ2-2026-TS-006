package com.eatsnow.security;

import java.util.Set;
import java.util.UUID;

/**
 * Caller identity taken from a verified Identity access token (Spec 3.1). restaurantId and driverId are the ownership
 * links Identity puts in the token; services never read them from the path, query or body.
 */
public record AuthenticatedUser(UUID userId, Set<String> roles, UUID restaurantId, UUID driverId) {
}

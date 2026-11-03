package com.eatsnow.common.domain.exception;

import java.util.Map;

/** Optimistic concurrency failure (Spec 3.5): `expectedVersion` does not match the stored version. */
public class VersionConflictException extends DomainException {
    public VersionConflictException(String message, int currentVersion) {
        super("VERSION_CONFLICT", message, 409, Map.of("currentVersion", currentVersion));
    }
}

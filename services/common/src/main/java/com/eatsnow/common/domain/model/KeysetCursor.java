package com.eatsnow.common.domain.model;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque cursor for lists ordered by (createdAt, id) (Spec 3.1). A null cursor means "first page"; it is represented by
 * the smallest possible position so a single keyset query serves every page.
 */
public record KeysetCursor(Instant createdAt, UUID id) {

    public static final KeysetCursor START = new KeysetCursor(Instant.EPOCH, new UUID(0L, 0L));

    public String encode() {
        String raw = createdAt.toEpochMilli() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidRequestException 400 when the cursor was not produced by this API */
    public static KeysetCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return START;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":");
            Instant at = Instant.ofEpochSecond(Long.parseLong(parts[0]) / 1000, Long.parseLong(parts[1]));
            return new KeysetCursor(at, UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidRequestException("The cursor is not valid");
        }
    }
}

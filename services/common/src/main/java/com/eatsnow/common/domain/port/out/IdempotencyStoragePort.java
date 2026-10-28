package com.eatsnow.common.domain.port.out;

import com.eatsnow.common.domain.model.IdempotencyEntry;

import java.util.Optional;

public interface IdempotencyStoragePort {

    Optional<IdempotencyEntry> find(String scopeKey);

    void start(String scopeKey, String requestHash);

    void complete(String scopeKey, String requestHash, int statusCode, String responseBody);

    void release(String scopeKey);
}

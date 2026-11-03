package com.eatsnow.orders.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/** Encrypted payment method token, kept only until Payments has an outcome (Spec 3.4). */
public interface OrderSecretPort {
    void store(UUID orderId, String encryptedPaymentToken);

    Optional<String> find(UUID orderId);

    void delete(UUID orderId);
}

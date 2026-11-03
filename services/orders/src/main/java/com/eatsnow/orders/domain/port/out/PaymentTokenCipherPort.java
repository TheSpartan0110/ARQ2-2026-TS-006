package com.eatsnow.orders.domain.port.out;

/** Encrypts the payment method token before it is stored or sent (Spec 4.2.5). */
public interface PaymentTokenCipherPort {
    String encrypt(String paymentMethodToken);
}

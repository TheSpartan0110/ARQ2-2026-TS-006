package com.eatsnow.payments.domain.port.out;

/** Decrypts the payment method token received in the ChargePayment command (Spec 4.2.5). */
public interface PaymentTokenCipherPort {
    String decrypt(String encryptedPaymentToken);
}

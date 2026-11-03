package com.eatsnow.payments.infrastructure.adapter.out.crypto;

import com.eatsnow.messaging.crypto.PaymentTokenCipher;
import com.eatsnow.payments.domain.port.out.PaymentTokenCipherPort;
import org.springframework.stereotype.Component;

@Component
public class AesPaymentTokenCipherAdapter implements PaymentTokenCipherPort {
    private final PaymentTokenCipher cipher;

    public AesPaymentTokenCipherAdapter(PaymentTokenCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String decrypt(String encryptedPaymentToken) {
        return cipher.decrypt(encryptedPaymentToken);
    }
}

package com.eatsnow.payments.infrastructure.adapter.out.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.port.out.PaymentProviderPort;
import org.junit.jupiter.api.Test;

class FakePaymentProviderTest {

    private static final Money AMOUNT = Money.cop(2500000);
    private final FakePaymentProvider provider = new FakePaymentProvider();

    @Test
    void sameKeyNeverChargesTwice() throws Exception {
        ProviderResult first = provider.charge("charge-1", AMOUNT, "tok_ok");
        ProviderResult second = provider.charge("charge-1", AMOUNT, "tok_ok");

        assertEquals(ProviderResult.Outcome.AUTHORIZED, first.outcome());
        assertEquals(first.providerReference(), second.providerReference());
    }

    @Test
    void declinedTokenIsADefinitiveRefusal() throws Exception {
        ProviderResult result = provider.charge("charge-2", AMOUNT, "tok_declined");

        assertEquals(ProviderResult.Outcome.FAILED, result.outcome());
        assertEquals("DECLINED", result.failureCode());
    }

    @Test
    void aLostResponseTimesOutButTheChargeIsFoundByLookup() {
        assertThrows(PaymentProviderPort.ProviderTimeoutException.class,
                () -> provider.charge("charge-3", AMOUNT, "tok_lost_response"));

        ProviderResult found = provider.lookup("charge-3").orElseThrow();
        assertEquals(ProviderResult.Outcome.AUTHORIZED, found.outcome());
    }

    @Test
    void aTimeoutThatWasNeverRecordedStaysUnresolved() {
        assertThrows(PaymentProviderPort.ProviderTimeoutException.class,
                () -> provider.charge("charge-4", AMOUNT, "tok_timeout"));

        assertTrue(provider.lookup("charge-4").isEmpty());
    }

    @Test
    void aRefundIsConfirmedAndNeverRepeatedForTheSameKey() throws Exception {
        provider.charge("charge-5", AMOUNT, "tok_ok");

        ProviderResult first = provider.refund("refund-5", "charge-5", AMOUNT);
        ProviderResult second = provider.refund("refund-5", "charge-5", AMOUNT);

        assertEquals(ProviderResult.Outcome.REFUNDED, first.outcome());
        assertEquals(first.providerReference(), second.providerReference());
    }

    @Test
    void aLostRefundResponseTimesOutButTheRefundIsFoundByLookup() throws Exception {
        provider.charge("charge-6", AMOUNT, "tok_refund_lost");

        assertThrows(PaymentProviderPort.ProviderTimeoutException.class,
                () -> provider.refund("refund-6", "charge-6", AMOUNT));

        assertEquals(ProviderResult.Outcome.REFUNDED, provider.lookupRefund("refund-6").orElseThrow().outcome());
    }

    @Test
    void aRefusedRefundIsAPermanentFailure() throws Exception {
        provider.charge("charge-7", AMOUNT, "tok_refund_refused");

        ProviderResult result = provider.refund("refund-7", "charge-7", AMOUNT);

        assertEquals(ProviderResult.Outcome.FAILED, result.outcome());
        assertEquals("REFUND_REFUSED", result.failureCode());
    }
}

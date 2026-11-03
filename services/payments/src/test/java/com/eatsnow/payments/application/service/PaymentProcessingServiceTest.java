package com.eatsnow.payments.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.port.out.PaymentProviderPort;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.domain.port.out.PaymentTokenCipherPort;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.model.RefundStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceTest {

    @Mock PaymentRepositoryPort payments;
    @Mock PaymentProviderPort provider;
    @Mock PaymentTokenCipherPort cipher;
    @Mock PaymentOutcomeService outcomes;
    @Mock RefundRepositoryPort refunds;
    @Mock RefundOutcomeService refundOutcomes;
    @InjectMocks PaymentProcessingService service;

    private Payment payment(PaymentStatus status) {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), Money.cop(2500000), "charge-1", "encrypted",
                UUID.randomUUID(), UUID.randomUUID(), null, status, 1);
    }

    @Test
    void chargeUsesTheStableIdempotencyKeyAndRecordsTheOutcome() throws Exception {
        Payment payment = payment(PaymentStatus.PENDING);
        ProviderResult authorized = ProviderResult.authorized("ref-1");
        when(cipher.decrypt("encrypted")).thenReturn("tok_ok");
        when(provider.charge("charge-1", Money.cop(2500000), "tok_ok")).thenReturn(authorized);

        service.charge(payment);

        verify(outcomes).apply(payment, authorized);
    }

    @Test
    void aProviderTimeoutIsRecordedAsUnknownNotAsFailure() throws Exception {
        Payment payment = payment(PaymentStatus.PENDING);
        when(cipher.decrypt("encrypted")).thenReturn("tok_timeout");
        when(provider.charge("charge-1", Money.cop(2500000), "tok_timeout"))
                .thenThrow(new PaymentProviderPort.ProviderTimeoutException("no answer"));

        service.charge(payment);

        verify(outcomes).apply(payment, ProviderResult.unknown());
    }

    @Test
    void reconciliationRecordsADefinitiveAnswer() {
        Payment payment = payment(PaymentStatus.UNKNOWN);
        ProviderResult authorized = ProviderResult.authorized("ref-1");
        when(provider.lookup("charge-1")).thenReturn(Optional.of(authorized));

        service.reconcile(payment);

        verify(outcomes).apply(payment, authorized);
    }

    @Test
    void reconciliationLeavesThePaymentUntouchedWhileTheProviderHasNoAnswer() {
        Payment payment = payment(PaymentStatus.UNKNOWN);
        when(provider.lookup("charge-1")).thenReturn(Optional.empty());

        service.reconcile(payment);

        verify(outcomes, never()).apply(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Refund refund() {
        return new Refund(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.cop(2500000), "refund-1",
                "charge-1", UUID.randomUUID(), UUID.randomUUID(), null, RefundStatus.PENDING, 1);
    }

    @Test
    void refundUsesTheStableRefundKeyAndRecordsTheOutcome() throws Exception {
        Refund refund = refund();
        ProviderResult refunded = ProviderResult.refunded("ref-9");
        when(provider.refund("refund-1", "charge-1", Money.cop(2500000))).thenReturn(refunded);

        service.refund(refund);

        verify(refundOutcomes).apply(refund, refunded);
    }

    @Test
    void aRefundTimeoutIsRecordedAsUnknownNotAsFailure() throws Exception {
        Refund refund = refund();
        when(provider.refund("refund-1", "charge-1", Money.cop(2500000)))
                .thenThrow(new PaymentProviderPort.ProviderTimeoutException("no answer"));

        service.refund(refund);

        verify(refundOutcomes).apply(refund, ProviderResult.unknown());
    }

    @Test
    void refundReconciliationRecordsADefinitiveAnswerOnly() {
        Refund refund = refund();
        when(provider.lookupRefund("refund-1")).thenReturn(Optional.of(ProviderResult.refunded("ref-9")),
                Optional.empty());

        service.reconcileRefund(refund);
        service.reconcileRefund(refund);

        verify(refundOutcomes, org.mockito.Mockito.times(1)).apply(refund, ProviderResult.refunded("ref-9"));
    }
}

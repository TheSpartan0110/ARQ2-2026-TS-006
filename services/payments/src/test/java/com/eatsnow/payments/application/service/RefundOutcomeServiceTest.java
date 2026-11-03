package com.eatsnow.payments.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.model.RefundStatus;
import com.eatsnow.payments.domain.port.out.PaymentEventPublisherPort;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundOutcomeServiceTest {

    @Mock PaymentRepositoryPort payments;
    @Mock RefundRepositoryPort refunds;
    @Mock PaymentEventPublisherPort events;

    private RefundOutcomeService service;
    private final Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), Money.cop(2500000), "charge-1",
            null, UUID.randomUUID(), UUID.randomUUID(), null, PaymentStatus.REFUND_PENDING, 3);
    private final Refund refund = new Refund(UUID.randomUUID(), payment.id(), payment.orderId(), Money.cop(2500000),
            "refund-1", "charge-1", UUID.randomUUID(), UUID.randomUUID(), null, RefundStatus.PENDING, 1);

    @BeforeEach
    void setUp() {
        service = new RefundOutcomeService(payments, refunds, events, new SimpleMeterRegistry());
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
    }

    @Test
    void aConfirmedRefundMarksThePaymentRefundedAndAnnouncesIt() {
        when(refunds.markRefunded(refund, "ref-9")).thenReturn(true);
        when(payments.markRefunded(payment)).thenReturn(true);

        service.apply(refund, ProviderResult.refunded("ref-9"));

        verify(events).paymentRefunded(refund, 4);
    }

    @Test
    void aRefundTimeoutIsAnnouncedAsPendingReconciliationForTheRefund() {
        when(refunds.markUnknown(refund)).thenReturn(true);

        service.apply(refund, ProviderResult.unknown());

        verify(events).refundPendingReconciliation(refund, 3);
        verify(payments, never()).markRefunded(any());     // the payment is NOT regressed or completed
    }

    @Test
    void aRepeatedTimeoutDoesNotEmitTheEventAgain() {
        when(refunds.markUnknown(refund)).thenReturn(false);

        service.apply(refund, ProviderResult.unknown());

        verifyNoInteractions(events);
    }

    @Test
    void aPermanentRefusalEmitsNoEventSoTheCustomerIsNeverReportedAsRefunded() {
        when(refunds.markFailed(refund, "REFUND_REFUSED")).thenReturn(true);

        service.apply(refund, ProviderResult.failed("REFUND_REFUSED"));

        verify(events, never()).paymentRefunded(any(), anyInt());
        verifyNoInteractions(events);
    }
}

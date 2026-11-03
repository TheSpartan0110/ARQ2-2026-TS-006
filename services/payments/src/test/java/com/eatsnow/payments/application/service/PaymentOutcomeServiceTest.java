package com.eatsnow.payments.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.port.out.PaymentEventPublisherPort;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOutcomeServiceTest {

    @Mock PaymentRepositoryPort payments;
    @Mock PaymentEventPublisherPort events;

    private PaymentOutcomeService service;
    private final Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), Money.cop(2500000),
            "charge-1", "encrypted", UUID.randomUUID(), UUID.randomUUID(), null, PaymentStatus.PENDING, 1);

    @BeforeEach
    void setUp() {
        service = new PaymentOutcomeService(payments, events, new SimpleMeterRegistry());
    }

    @Test
    void authorizedOutcomeIsStoredAndAnnounced() {
        when(payments.markAuthorized(payment, "ref-1")).thenReturn(true);

        service.apply(payment, ProviderResult.authorized("ref-1"));

        verify(events).paymentAuthorized(payment, 2);
    }

    @Test
    void anOutcomeThatLostTheRaceEmitsNothing() {
        when(payments.markAuthorized(payment, "ref-1")).thenReturn(false);   // changed in the meantime

        service.apply(payment, ProviderResult.authorized("ref-1"));

        verifyNoInteractions(events);
    }

    @Test
    void definitiveRefusalIsAnnouncedWithItsFailureCode() {
        when(payments.markFailed(payment, "DECLINED")).thenReturn(true);

        service.apply(payment, ProviderResult.failed("DECLINED"));

        verify(events).paymentFailed(payment, "DECLINED", 2);
    }

    @Test
    void aTimeoutIsAnnouncedAsPendingReconciliationAndNeverAsFailure() {
        when(payments.markUnknown(payment)).thenReturn(true);

        service.apply(payment, ProviderResult.unknown());

        verify(events).paymentPendingReconciliation(payment, 2);
        verify(events, never()).paymentFailed(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aRepeatedTimeoutDoesNotEmitTheEventAgain() {
        when(payments.markUnknown(payment)).thenReturn(false);   // already UNKNOWN

        service.apply(payment, ProviderResult.unknown());

        verifyNoInteractions(events);
    }
}

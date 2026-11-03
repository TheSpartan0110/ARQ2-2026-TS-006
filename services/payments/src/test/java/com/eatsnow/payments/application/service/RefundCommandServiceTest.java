package com.eatsnow.payments.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.exception.InconsistentCommandException;
import com.eatsnow.payments.domain.exception.PaymentNotReadyException;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.port.in.RecordRefundUseCase;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundCommandServiceTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Money AMOUNT = Money.cop(2500000);

    @Mock PaymentRepositoryPort payments;
    @Mock RefundRepositoryPort refunds;
    @InjectMocks RefundCommandService service;

    private Payment payment(PaymentStatus status) {
        return new Payment(PAYMENT_ID, ORDER_ID, AMOUNT, "charge-" + ORDER_ID, null, UUID.randomUUID(),
                UUID.randomUUID(), null, status, 2);
    }

    private RecordRefundUseCase.Command command(Money amount) {
        return new RecordRefundUseCase.Command(ORDER_ID, PAYMENT_ID, amount, UUID.randomUUID(), UUID.randomUUID(), null);
    }

    @Test
    void recordsOneRefundWithAKeyDerivedFromThePaymentAndMarksTheRefundPending() {
        Payment payment = payment(PaymentStatus.AUTHORIZED);
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refunds.insertPending(any())).thenReturn(true);

        assertTrue(service.recordRefund(command(AMOUNT)));

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refunds).insertPending(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("refund-" + PAYMENT_ID, captor.getValue().idempotencyKey());
        verify(payments).markRefundPending(payment);
    }

    @Test
    void aDuplicateCommandChangesNothing() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment(PaymentStatus.REFUND_PENDING)));
        when(refunds.insertPending(any())).thenReturn(false);

        assertFalse(service.recordRefund(command(AMOUNT)));

        verify(payments, never()).markRefundPending(any());
    }

    @Test
    void aRefundBeforeTheChargeIsDefinitiveIsRetriedLater() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment(PaymentStatus.UNKNOWN)));

        assertThrows(PaymentNotReadyException.class, () -> service.recordRefund(command(AMOUNT)));
        verify(refunds, never()).insertPending(any());
    }

    @Test
    void aDifferentAmountIsQuarantined() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment(PaymentStatus.AUTHORIZED)));

        assertThrows(InconsistentCommandException.class, () -> service.recordRefund(command(Money.cop(1))));
    }

    @Test
    void anUnknownPaymentIsQuarantined() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(InconsistentCommandException.class, () -> service.recordRefund(command(AMOUNT)));
    }

    @Test
    void aFailedPaymentCannotBeRefunded() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment(PaymentStatus.FAILED)));

        assertThrows(InconsistentCommandException.class, () -> service.recordRefund(command(AMOUNT)));
    }
}

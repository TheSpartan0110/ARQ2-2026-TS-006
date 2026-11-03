package com.eatsnow.notifications.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.notifications.domain.model.Notification;
import com.eatsnow.notifications.domain.model.NotificationStatus;
import com.eatsnow.notifications.domain.model.NotificationTrigger;
import com.eatsnow.notifications.domain.port.in.ProcessOrderStatusChangeUseCase;
import com.eatsnow.notifications.domain.port.out.NotificationRepositoryPort;
import com.eatsnow.notifications.domain.port.out.OrderVersionPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationRecordingServiceTest {

    private static final UUID ORDER = UUID.randomUUID();

    @Mock OrderVersionPort versions;
    @Mock NotificationRepositoryPort notifications;
    @InjectMocks NotificationRecordingService service;

    private ProcessOrderStatusChangeUseCase.Change change(int version, String previous, String status, String payment) {
        return new ProcessOrderStatusChangeUseCase.Change(ORDER, UUID.randomUUID(), version, previous, status, payment,
                UUID.randomUUID());
    }

    @Test
    void aStatusChangeToOnTheWayRecordsOnePendingNotification() {
        when(versions.advance(ORDER, 5)).thenReturn(true);

        service.process(change(5, "PREPARING", "ON_THE_WAY", "AUTHORIZED"));

        verify(notifications).insertIfAbsent(argThat((Notification n) ->
                n.trigger() == NotificationTrigger.ON_THE_WAY && n.status() == NotificationStatus.PENDING
                        && n.attempts() == 0));
    }

    @Test
    void anOlderVersionIsAcknowledgedWithoutSending() {
        when(versions.advance(ORDER, 3)).thenReturn(false);

        service.process(change(3, "CREATED", "ACCEPTED", "AUTHORIZED"));

        verifyNoInteractions(notifications);
    }

    @Test
    void changesThatAreNotTriggersAreAcknowledgedWithoutSending() {
        when(versions.advance(ORDER, 2)).thenReturn(true);

        service.process(change(2, "CREATED", "CREATED", "AUTHORIZED"));     // only the payment status changed
        service.process(change(2, "ACCEPTED", "PREPARING", "AUTHORIZED"));  // PREPARING has no email

        verify(notifications, never()).insertIfAbsent(any());
    }

    @Test
    void aPaymentRefundedChangeRecordsTheRefundNotification() {
        when(versions.advance(ORDER, 4)).thenReturn(true);

        service.process(change(4, "REJECTED", "REJECTED", "REFUNDED"));

        verify(notifications).insertIfAbsent(argThat((Notification n) -> n.trigger() == NotificationTrigger.REFUNDED));
    }

    @Test
    void paymentFailedAndRejectedAreTriggers() {
        when(versions.advance(ORDER, 2)).thenReturn(true);

        service.process(change(2, "CREATED", "PAYMENT_FAILED", "FAILED"));
        service.process(change(2, "CREATED", "REJECTED", "REFUND_PENDING"));

        verify(notifications).insertIfAbsent(argThat((Notification n) -> n.trigger() == NotificationTrigger.PAYMENT_FAILED));
        verify(notifications).insertIfAbsent(argThat((Notification n) -> n.trigger() == NotificationTrigger.REJECTED));
    }
}

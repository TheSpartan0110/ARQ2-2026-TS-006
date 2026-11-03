package com.eatsnow.notifications.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eatsnow.notifications.domain.exception.ContactNotFoundException;
import com.eatsnow.notifications.domain.exception.TransientNotificationException;
import com.eatsnow.notifications.domain.model.CustomerContact;
import com.eatsnow.notifications.domain.model.Notification;
import com.eatsnow.notifications.domain.model.NotificationStatus;
import com.eatsnow.notifications.domain.model.NotificationTrigger;
import com.eatsnow.notifications.domain.port.out.ContactLookupPort;
import com.eatsnow.notifications.domain.port.out.EmailSenderPort;
import com.eatsnow.notifications.domain.port.out.NotificationRepositoryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
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
class NotificationDispatchServiceTest {

    @Mock NotificationRepositoryPort notifications;
    @Mock ContactLookupPort contacts;
    @Mock EmailSenderPort emails;

    private NotificationDispatchService service;
    private final UUID customer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(notifications, contacts, emails, new SimpleMeterRegistry());
        when(contacts.find(customer)).thenReturn(new CustomerContact(customer, "Alex", "alex@example.test"));
    }

    private Notification notification(int attempts) {
        return new Notification(UUID.randomUUID(), UUID.randomUUID(), customer, NotificationTrigger.ON_THE_WAY,
                NotificationStatus.PENDING, attempts, Instant.now(), UUID.randomUUID(), 1 + attempts);
    }

    @Test
    void sendsTheEmailAndMarksTheNotificationSent() {
        Notification n = notification(0);
        when(notifications.markSent(n)).thenReturn(true);

        service.dispatch(n);

        verify(emails).send(eq("alex@example.test"), anyString(), anyString());
        verify(notifications).markSent(n);
    }

    @Test
    void aProviderOutageSchedulesTheFirstRetryAfterFiveSeconds() {
        Notification n = notification(0);
        doThrow(new TransientNotificationException("smtp down", null)).when(emails).send(anyString(), anyString(), anyString());

        service.dispatch(n);

        verify(notifications).scheduleRetry(eq(n), eq(1), org.mockito.ArgumentMatchers.argThat(
                (Instant next) -> next.isAfter(Instant.now().plusSeconds(3)) && next.isBefore(Instant.now().plusSeconds(7))));
        verify(notifications, never()).markFailed(any());
    }

    @Test
    void theThirdRetryWaitsTwoMinutes() {
        Notification n = notification(2);
        doThrow(new TransientNotificationException("smtp down", null)).when(emails).send(anyString(), anyString(), anyString());

        service.dispatch(n);

        verify(notifications).scheduleRetry(eq(n), eq(3), org.mockito.ArgumentMatchers.argThat(
                (Instant next) -> next.isAfter(Instant.now().plusSeconds(115))));
    }

    @Test
    void afterTheLastRetryTheNotificationIsMarkedFailedWithoutTouchingTheOrder() {
        Notification n = notification(3);
        doThrow(new TransientNotificationException("smtp down", null)).when(emails).send(anyString(), anyString(), anyString());
        when(notifications.markFailed(n)).thenReturn(true);

        service.dispatch(n);

        verify(notifications).markFailed(n);
        verify(notifications, never()).scheduleRetry(any(), anyInt(), any());
    }

    @Test
    void aMissingContactFailsPermanentlyWithoutRetrying() {
        Notification n = notification(0);
        when(contacts.find(customer)).thenThrow(new ContactNotFoundException("none"));
        when(notifications.markFailed(n)).thenReturn(true);

        service.dispatch(n);

        verify(notifications).markFailed(n);
        verify(emails, never()).send(anyString(), anyString(), anyString());
    }
}

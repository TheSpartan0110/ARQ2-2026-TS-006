package com.eatsnow.orders.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.orders.domain.model.DeliveryAddress;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderItem;
import com.eatsnow.orders.domain.model.OrderReceipt;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.model.PaymentStatus;
import com.eatsnow.orders.domain.model.Quote;
import com.eatsnow.orders.domain.port.in.CreateOrderUseCase;
import com.eatsnow.orders.domain.port.out.OrderEventPublisherPort;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import com.eatsnow.orders.domain.port.out.OrderSecretPort;
import com.eatsnow.orders.domain.port.out.PaymentTokenCipherPort;
import com.eatsnow.orders.domain.port.out.QuoteVerifierPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID KEY = UUID.fromString("00000000-0000-4000-8000-000000000030");
    private static final UUID CORRELATION = UUID.fromString("00000000-0000-4000-8000-000000000040");
    private static final String RAW = "{\"quoteToken\":\"q\"}";

    @Mock OrderRepositoryPort orders;
    @Mock OrderSecretPort secrets;
    @Mock QuoteVerifierPort quoteVerifier;
    @Mock PaymentTokenCipherPort cipher;
    @Mock OrderEventPublisherPort events;
    @Mock OrderTransitions transitions;
    @Mock IdempotencyStoragePort idempotency;

    private CreateOrderService service;

    @BeforeEach
    void setUp() {
        service = new CreateOrderService(orders, secrets, quoteVerifier, cipher, events, transitions, idempotency,
                new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry());
    }

    private CreateOrderUseCase.Command command() {
        return new CreateOrderUseCase.Command(CUSTOMER, KEY, "/api/v1/orders", RAW, "q",
                new DeliveryAddress("123 Example Street", "Medellin", null), "tok_ok", CORRELATION);
    }

    private static String scopeKey() {
        return CUSTOMER + ":POST:/api/v1/orders:" + KEY;
    }

    private static String hash(String data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void createsTheOrderAndItsOutboxEntriesAndStoresTheResponse() {
        Quote quote = new Quote(UUID.randomUUID(), CUSTOMER, UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), "Classic Burger", 1, Money.cop(2500000))),
                Money.cop(2500000), Instant.now().plusSeconds(60));
        when(idempotency.find(scopeKey())).thenReturn(Optional.empty());
        when(quoteVerifier.verify("q", CUSTOMER)).thenReturn(quote);
        when(cipher.encrypt("tok_ok")).thenReturn("encrypted");

        OrderReceipt receipt = service.createOrder(command());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).insert(captor.capture());
        Order order = captor.getValue();
        assertEquals(OrderStatus.CREATED, order.status());
        assertEquals(PaymentStatus.PENDING, order.paymentStatus());
        assertEquals(CORRELATION, order.correlationId());       // the HTTP request creates the workflow id
        assertEquals(CORRELATION, receipt.correlationId());
        assertEquals(order.id(), receipt.orderId());
        verify(secrets).store(order.id(), "encrypted");
        verify(events).orderCreated(order);
        verify(transitions).recordCreation(order);
        verify(idempotency).start(eq(scopeKey()), anyString());
        verify(idempotency).complete(eq(scopeKey()), anyString(), eq(202), argThat(body -> body.contains("CREATED")));
    }

    @Test
    void replaysTheOriginalResponseWithoutCreatingAnotherOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        String stored = "{\"orderId\":\"" + orderId + "\",\"status\":\"CREATED\",\"correlationId\":\"" + CORRELATION + "\"}";
        when(idempotency.find(scopeKey())).thenReturn(Optional.of(
                new IdempotencyEntry(scopeKey(), hash(RAW), "COMPLETED", 202, stored, Instant.now())));

        OrderReceipt receipt = service.createOrder(command());

        assertEquals(orderId, receipt.orderId());
        verifyNoInteractions(orders, secrets, events, transitions, quoteVerifier);   // not even an expired quote matters
        verify(idempotency, never()).start(anyString(), anyString());
    }

    @Test
    void sameKeyWithDifferentBodyIsAConflict() {
        when(idempotency.find(scopeKey())).thenReturn(Optional.of(
                new IdempotencyEntry(scopeKey(), "another-hash", "COMPLETED", 202, "{}", Instant.now())));

        assertThrows(IdempotencyConflictException.class, () -> service.createOrder(command()));
        verifyNoInteractions(orders, events);
    }

    @Test
    void aStillRunningAttemptReturnsRequestInProgress() {
        when(idempotency.find(scopeKey())).thenReturn(Optional.of(
                new IdempotencyEntry(scopeKey(), "h", "IN_PROGRESS", 0, null, Instant.now())));

        assertThrows(RequestInProgressException.class, () -> service.createOrder(command()));
        verifyNoInteractions(orders, events);
    }

    @Test
    void anInvalidQuoteIsNotCachedAndCreatesNothing() {
        when(idempotency.find(scopeKey())).thenReturn(Optional.empty());
        when(quoteVerifier.verify("q", CUSTOMER))
                .thenThrow(new com.eatsnow.orders.domain.exception.InvalidQuoteException("bad"));

        assertThrows(com.eatsnow.orders.domain.exception.InvalidQuoteException.class, () -> service.createOrder(command()));
        verify(idempotency, never()).complete(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
        verifyNoInteractions(orders, events);
    }
}

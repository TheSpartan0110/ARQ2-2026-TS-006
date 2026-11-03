package com.eatsnow.messaging.contract;

import com.eatsnow.common.domain.model.Money;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payload schemas of spec 4.2.3 (only the messages implemented so far). Unknown fields are ignored. */
public final class Payloads {
    private Payloads() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(UUID menuItemId, String name, int quantity, Money unitPrice) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderCreated(UUID customerId, UUID restaurantId, List<Item> items, Money total) { }

    /** paymentMethodToken is encrypted (spec 4.2.5) and must never be logged. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChargePayment(Money total, String paymentMethodToken) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentAuthorized(UUID paymentId, Money total) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentFailed(UUID paymentId, Money total, String failureCode) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentPendingReconciliation(UUID paymentId, String operation, Instant pendingSince) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderStatusChanged(UUID customerId, String previousStatus, String status, String paymentStatus) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String line1, String city, String instructions) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestRestaurantAcceptance(UUID restaurantId, List<Item> items) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundPayment(UUID paymentId, Money amount, String reason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentRefunded(UUID paymentId, Money amount) { }

    /** RestaurantAccepted and RestaurantPreparing share this schema. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestaurantDecision(UUID restaurantId) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestaurantRejected(UUID restaurantId, String reason) { }

    /** deliveryAddress is sensitive: it appears only in this command and must never be logged (Spec 4.2.5). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssignDelivery(UUID restaurantId, Address deliveryAddress) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryAssigned(UUID deliveryId, UUID driverId) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryPickedUp(UUID deliveryId, UUID driverId, Instant pickedUpAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryCompleted(UUID deliveryId, UUID driverId, Instant deliveredAt) { }
}

package com.eatsnow.orders.domain.model;

/** A valid but premature event stored durably until its prerequisite is met (Spec 4.3 item 3). */
public record PendingEvent(long id, OrderEvent event) {
}

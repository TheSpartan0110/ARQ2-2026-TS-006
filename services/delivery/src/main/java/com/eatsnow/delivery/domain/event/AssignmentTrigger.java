package com.eatsnow.delivery.domain.event;

/**
 * Raised when an assignment attempt is worthwhile: a delivery was registered, a driver went on duty or a driver
 * completed a delivery (Spec 3.6). Handled after the transaction commits.
 */
public record AssignmentTrigger(String reason) {
}

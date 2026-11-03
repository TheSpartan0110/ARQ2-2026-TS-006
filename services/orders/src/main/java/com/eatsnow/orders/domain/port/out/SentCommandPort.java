package com.eatsnow.orders.domain.port.out;

import java.util.UUID;

/** "Send each command once per order" (Spec 2.4). */
public interface SentCommandPort {
    /** @return true the first time the command is recorded for the order, false if it was already sent */
    boolean markSent(UUID orderId, String commandType);
}

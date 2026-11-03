package com.eatsnow.messaging.support;

import java.util.UUID;
import org.slf4j.MDC;

/** MDC keys written on every log line (Spec 6.2 workflow fields). */
public final class LogContext {
    private LogContext() { }

    public static final String CORRELATION_ID = "correlationId";
    public static final String ORDER_ID = "orderId";
    public static final String MESSAGE_ID = "messageId";
    public static final String MESSAGE_TYPE = "messageType";
    public static final String CAUSATION_ID = "causationId";

    public static void workflow(UUID orderId, UUID correlationId) {
        if (orderId != null) MDC.put(ORDER_ID, orderId.toString());
        if (correlationId != null) MDC.put(CORRELATION_ID, correlationId.toString());
    }

    public static void clear() {
        MDC.remove(ORDER_ID);
        MDC.remove(CORRELATION_ID);
        MDC.remove(MESSAGE_ID);
        MDC.remove(MESSAGE_TYPE);
        MDC.remove(CAUSATION_ID);
    }
}

package com.eatsnow.common.web.filter;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "requestCorrelationId";

    private CorrelationContext() {}

    public static UUID getCurrentCorrelationId() {
        String val = MDC.get(MDC_KEY);
        if (val != null) {
            try {
                return UUID.fromString(val);
            } catch (Exception ignored) {}
        }
        return UUID.randomUUID();
    }
}

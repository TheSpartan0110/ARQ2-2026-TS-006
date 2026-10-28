package com.eatsnow.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that enforces X-Correlation-ID header on all HTTP requests and responses (Spec 3.1 & 6.2).
 * Populates MDC with 'requestCorrelationId' for JSON structured logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationHeader = request.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
        UUID correlationId;

        if (correlationHeader != null && !correlationHeader.isBlank()) {
            try {
                correlationId = UUID.fromString(correlationHeader.trim());
            } catch (IllegalArgumentException ex) {
                correlationId = UUID.randomUUID();
            }
        } else {
            correlationId = UUID.randomUUID();
        }

        String correlationIdStr = correlationId.toString();
        MDC.put(CorrelationContext.MDC_KEY, correlationIdStr);
        response.setHeader(CorrelationContext.CORRELATION_ID_HEADER, correlationIdStr);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }
}

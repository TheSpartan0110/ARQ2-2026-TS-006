package com.eatsnow.common.web.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Returns the trace id of the request in X-Trace-Id (Spec 6.3), so a response seen in Postman or the browser can be
 * opened directly in Jaeger or Tempo. Only the random identifier is exposed. It runs right after the server
 * observation filter (HIGHEST_PRECEDENCE + 1), which opens the request span, and before authentication, so 401
 * responses carry it too. Through the Gateway the id is the same: the Gateway and the service share the trace.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TraceIdResponseFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final ObjectProvider<Tracer> tracer;

    public TraceIdResponseFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Tracer current = tracer.getIfAvailable();
        Span span = current != null ? current.currentSpan() : null;
        if (span != null) {
            response.setHeader(TRACE_ID_HEADER, span.context().traceId());
        }
        filterChain.doFilter(request, response);
    }
}

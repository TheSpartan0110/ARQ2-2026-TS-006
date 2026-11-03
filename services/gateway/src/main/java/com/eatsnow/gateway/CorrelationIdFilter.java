package com.eatsnow.gateway;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Correlation at the edge (Spec 3.1): reuse a valid X-Correlation-ID or generate one, forward it to the service,
 * return it on every response and write one structured access-log line per request.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    public static final String HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = valid(exchange.getRequest().getHeaders().getFirst(HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, correlationId).build();

        // beforeCommit runs after the downstream headers were copied, so the header appears exactly once.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, correlationId);
            return Mono.empty();
        });

        long start = System.nanoTime();
        String operation = exchange.getRequest().getMethod() + " " + exchange.getRequest().getPath().value();
        return chain.filter(exchange.mutate().request(request).build()).doFinally(signal -> {
            var status = exchange.getResponse().getStatusCode();
            int code = status != null ? status.value() : 0;
            log.info("Request routed",
                    kv("operation", operation),
                    kv("outcome", code >= 500 ? "FAILURE" : code >= 400 ? "REJECTED" : "SUCCESS"),
                    kv("requestCorrelationId", correlationId),
                    kv("httpStatus", code),
                    kv("durationMs", (System.nanoTime() - start) / 1_000_000));
        });
    }

    private static String valid(String header) {
        if (header != null) {
            try {
                return UUID.fromString(header.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // invalid: generate a new one (Spec 3.1)
            }
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

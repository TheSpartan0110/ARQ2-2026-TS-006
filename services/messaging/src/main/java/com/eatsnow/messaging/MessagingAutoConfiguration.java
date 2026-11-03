package com.eatsnow.messaging;

import com.eatsnow.messaging.consumer.EnvelopeConsumer;
import com.eatsnow.messaging.consumer.ProcessedMessages;
import com.eatsnow.messaging.crypto.PaymentTokenCipher;
import com.eatsnow.messaging.outbox.OutboxWriter;
import com.eatsnow.messaging.publisher.MessagePublisher;
import com.eatsnow.messaging.relay.OutboxRelay;
import com.eatsnow.messaging.tracing.TraceSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers the messaging building blocks in every service that has this module on its classpath.
 * Services only scan com.eatsnow.<service> and com.eatsnow.common (AGENTS.md), so this uses auto-configuration.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration"})
public class MessagingAutoConfiguration {

    @Bean
    public TraceSupport traceSupport(Tracer tracer, Propagator propagator) {
        return new TraceSupport(tracer, propagator);
    }

    @Bean
    public OutboxWriter outboxWriter(JdbcClient jdbc, TraceSupport trace) {
        return new OutboxWriter(jdbc, trace);
    }

    @Bean
    public MessagePublisher messagePublisher(RabbitTemplate rabbitTemplate) {
        return new MessagePublisher(rabbitTemplate);
    }

    @Bean
    public ProcessedMessages processedMessages(JdbcClient jdbc) {
        return new ProcessedMessages(jdbc);
    }

    @Bean
    public EnvelopeConsumer envelopeConsumer(TransactionTemplate tx, ProcessedMessages processed,
                                             MessagePublisher publisher, TraceSupport trace, MeterRegistry registry) {
        return new EnvelopeConsumer(tx, processed, publisher, trace, registry);
    }

    @Bean
    @ConditionalOnProperty(name = "eatsnow.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(JdbcClient jdbc, TransactionTemplate tx, MessagePublisher publisher,
                                   TraceSupport trace, MeterRegistry registry) {
        return new OutboxRelay(jdbc, tx, publisher, trace, registry);
    }

    @Bean
    @ConditionalOnProperty(name = "eatsnow.payment.token-key")
    public PaymentTokenCipher paymentTokenCipher(@Value("${eatsnow.payment.token-key}") String key) {
        return new PaymentTokenCipher(key);
    }
}

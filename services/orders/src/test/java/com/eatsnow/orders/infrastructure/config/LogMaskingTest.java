package com.eatsnow.orders.infrastructure.config;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.OutputStreamAppender;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The deny-list of logback-spring.xml (Spec 6.5) masks sensitive values even when code logs them by mistake. */
class LogMaskingTest {

    private static String encode(String message, Object... arguments) throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(LogMaskingTest.class.getResource("/logback-spring.xml"));
        @SuppressWarnings("unchecked")
        OutputStreamAppender<ILoggingEvent> appender = (OutputStreamAppender<ILoggingEvent>)
                context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("JSON_STDOUT");
        LoggingEvent event = new LoggingEvent(LogMaskingTest.class.getName(), context.getLogger("test"), Level.INFO,
                message, null, arguments);
        return new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8);
    }

    @Test
    void masksSensitiveFieldsByName() throws Exception {
        String line = encode("Order received", kv("email", "alex@example.test"),
                kv("paymentMethodToken", "tok_ok_4242"), kv("line1", "123 Example Street"), kv("outcome", "SUCCESS"));

        assertFalse(line.contains("alex@example.test"), line);
        assertFalse(line.contains("tok_ok_4242"), line);
        assertFalse(line.contains("123 Example Street"), line);
        assertTrue(line.contains("\"outcome\":\"SUCCESS\""), line);
    }

    @Test
    void masksSensitiveValuesInsideMessages() throws Exception {
        String line = encode("Calling with Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl "
                + "for alex@example.test paying with tok_declined_0002");

        assertFalse(line.contains("eyJhbGciOiJSUzI1NiJ9"), line);
        assertFalse(line.contains("alex@example.test"), line);
        assertFalse(line.contains("tok_declined_0002"), line);
        assertTrue(line.contains("[REDACTED]"), line);
    }
}

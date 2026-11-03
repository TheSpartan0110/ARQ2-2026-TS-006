package com.eatsnow.payments.infrastructure.adapter.out.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.messaging.consumer.EnvelopeHandler;
import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.messaging.support.PoisonMessageException;
import com.eatsnow.payments.domain.exception.InconsistentCommandException;
import com.eatsnow.payments.domain.port.in.RecordChargeUseCase;
import com.eatsnow.payments.domain.port.in.RecordRefundUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Translates a consumed command into a call to the payments use case. */
@Component
public class PaymentCommandHandler implements EnvelopeHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);

    private final RecordChargeUseCase recordCharge;
    private final RecordRefundUseCase recordRefund;

    public PaymentCommandHandler(RecordChargeUseCase recordCharge, RecordRefundUseCase recordRefund) {
        this.recordCharge = recordCharge;
        this.recordRefund = recordRefund;
    }

    @Override
    public void handle(MessageEnvelope<JsonNode> env) {
        if (MessageTypes.REFUND_PAYMENT.equals(env.messageType())) {
            handleRefund(env);
            return;
        }
        if (!MessageTypes.CHARGE_PAYMENT.equals(env.messageType())) {
            log.warn("Command not handled", kv("operation", "consume " + env.messageType()), kv("outcome", "FAILURE"));
            return;
        }
        Payloads.ChargePayment command = MessageJson.readPayload(env, Payloads.ChargePayment.class);
        if (command.total() == null || command.paymentMethodToken() == null) {
            throw new PoisonMessageException("ChargePayment is missing total or paymentMethodToken");
        }
        recordCharge.recordCharge(new RecordChargeUseCase.Command(env.orderId(), command.total(),
                command.paymentMethodToken(), env.correlationId(), env.messageId(), env.traceparent()));
    }

    private void handleRefund(MessageEnvelope<JsonNode> env) {
        Payloads.RefundPayment command = MessageJson.readPayload(env, Payloads.RefundPayment.class);
        if (command.paymentId() == null || command.amount() == null) {
            throw new PoisonMessageException("RefundPayment is missing paymentId or amount");
        }
        try {
            recordRefund.recordRefund(new RecordRefundUseCase.Command(env.orderId(), command.paymentId(),
                    command.amount(), env.correlationId(), env.messageId(), env.traceparent()));
        } catch (InconsistentCommandException e) {
            throw new PoisonMessageException(e.getMessage(), e);   // retrying cannot help: DLQ
        }
        // PaymentNotReadyException is transient: it propagates and the consumer retries after 5, 30 and 120 s.
    }
}

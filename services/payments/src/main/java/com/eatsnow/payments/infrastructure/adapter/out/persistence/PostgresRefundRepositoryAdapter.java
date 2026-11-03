package com.eatsnow.payments.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.model.RefundStatus;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import com.eatsnow.payments.infrastructure.adapter.out.persistence.entity.RefundEntity;
import com.eatsnow.payments.infrastructure.adapter.out.persistence.repository.SpringDataRefundRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresRefundRepositoryAdapter implements RefundRepositoryPort {

    private final SpringDataRefundRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresRefundRepositoryAdapter(SpringDataRefundRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean insertPending(Refund r) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO payments.refunds (id, payment_id, order_id, amount_minor, currency, status,
                                              provider_idempotency_key, charge_idempotency_key, correlation_id,
                                              command_message_id, traceparent, version)
                VALUES (:id, :paymentId, :orderId, :amount, :currency, 'PENDING', :key, :chargeKey, :correlationId,
                        :commandId, :traceparent, 1)
                ON CONFLICT (payment_id) DO NOTHING
                """)
                .setParameter("id", r.id())
                .setParameter("paymentId", r.paymentId())
                .setParameter("orderId", r.orderId())
                .setParameter("amount", r.amount().amountMinor())
                .setParameter("currency", r.amount().currency())
                .setParameter("key", r.idempotencyKey())
                .setParameter("chargeKey", r.chargeIdempotencyKey())
                .setParameter("correlationId", r.correlationId())
                .setParameter("commandId", r.commandMessageId())
                .setParameter("traceparent", r.traceparent())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> findByStatus(RefundStatus status, int limit) {
        return repository.findByStatusOrdered(status.name(), PageRequest.of(0, limit)).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markRefunded(Refund refund, String providerReference) {
        return repository.markRefunded(refund.id(), refund.version(), providerReference, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean markFailed(Refund refund, String failureCode) {
        return repository.markFailed(refund.id(), refund.version(), failureCode, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean markUnknown(Refund refund) {
        return repository.markUnknown(refund.id(), refund.version(), Instant.now()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestUnresolvedSeconds() {
        Object seconds = entityManager.createNativeQuery("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at))), 0)
                FROM payments.refunds WHERE status IN ('PENDING', 'UNKNOWN', 'FAILED')
                """).getSingleResult();
        return ((Number) seconds).longValue();
    }

    private Refund toDomain(RefundEntity e) {
        return new Refund(e.getId(), e.getPaymentId(), e.getOrderId(), new Money(e.getAmountMinor(), e.getCurrency()),
                e.getProviderIdempotencyKey(), e.getChargeIdempotencyKey(), e.getCorrelationId(),
                e.getCommandMessageId(), e.getTraceparent(), RefundStatus.valueOf(e.getStatus()), e.getVersion());
    }
}

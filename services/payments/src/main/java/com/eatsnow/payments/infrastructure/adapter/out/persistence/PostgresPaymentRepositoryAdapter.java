package com.eatsnow.payments.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.eatsnow.payments.infrastructure.adapter.out.persistence.repository.SpringDataPaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresPaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final SpringDataPaymentRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresPaymentRepositoryAdapter(SpringDataPaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean insertPending(Payment p) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO payments.payments (id, order_id, total_amount_minor, currency, status,
                                               provider_idempotency_key, encrypted_payment_token, correlation_id,
                                               command_message_id, traceparent, version)
                VALUES (:id, :orderId, :amount, :currency, 'PENDING', :key, :token, :correlationId, :commandId,
                        :traceparent, 1)
                ON CONFLICT (order_id) DO NOTHING
                """)
                .setParameter("id", p.id())
                .setParameter("orderId", p.orderId())
                .setParameter("amount", p.total().amountMinor())
                .setParameter("currency", p.total().currency())
                .setParameter("key", p.idempotencyKey())
                .setParameter("token", p.encryptedPaymentToken())
                .setParameter("correlationId", p.correlationId())
                .setParameter("commandId", p.commandMessageId())
                .setParameter("traceparent", p.traceparent())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findByStatus(PaymentStatus status, int limit) {
        return repository.findByStatusOrdered(status.name(), PageRequest.of(0, limit)).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markAuthorized(Payment payment, String providerReference) {
        return repository.markAuthorized(payment.id(), payment.version(), providerReference, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean markFailed(Payment payment, String failureCode) {
        return repository.markFailed(payment.id(), payment.version(), failureCode, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean markUnknown(Payment payment) {
        return repository.markUnknown(payment.id(), payment.version(), Instant.now()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestUnresolvedSeconds() {
        Object seconds = entityManager.createNativeQuery("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(pending_since))), 0)
                FROM payments.payments WHERE status = 'UNKNOWN'
                """).getSingleResult();
        return ((Number) seconds).longValue();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID paymentId) {
        return repository.findById(paymentId).map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean markRefundPending(Payment payment) {
        return repository.markRefundPending(payment.id(), payment.version(), Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean markRefunded(Payment payment) {
        return repository.markRefunded(payment.id(), payment.version(), Instant.now()) == 1;
    }

    private Payment toDomain(PaymentEntity e) {
        return new Payment(e.getId(), e.getOrderId(), new Money(e.getTotalAmountMinor(), e.getCurrency()),
                e.getProviderIdempotencyKey(), e.getEncryptedPaymentToken(), e.getCorrelationId(),
                e.getCommandMessageId(), e.getTraceparent(), PaymentStatus.valueOf(e.getStatus()), e.getVersion());
    }
}

package com.eatsnow.payments.application.service;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.model.RefundStatus;
import com.eatsnow.payments.domain.port.in.PaymentProcessingUseCase;
import com.eatsnow.payments.domain.port.out.PaymentProviderPort;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.domain.port.out.PaymentTokenCipherPort;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not transactional on purpose: the provider is an external call and must never hold a database transaction open
 * (Spec 4.3 item 5). Each recorded outcome is its own short transaction in PaymentOutcomeService.
 * Wave 2 assumes ONE Payments replica; with several, claim rows with FOR UPDATE SKIP LOCKED before charging.
 */
@Service
public class PaymentProcessingService implements PaymentProcessingUseCase {

    private final PaymentRepositoryPort payments;
    private final PaymentProviderPort provider;
    private final PaymentTokenCipherPort cipher;
    private final PaymentOutcomeService outcomes;
    private final RefundRepositoryPort refunds;
    private final RefundOutcomeService refundOutcomes;

    public PaymentProcessingService(PaymentRepositoryPort payments, PaymentProviderPort provider,
                                    PaymentTokenCipherPort cipher, PaymentOutcomeService outcomes,
                                    RefundRepositoryPort refunds, RefundOutcomeService refundOutcomes) {
        this.payments = payments;
        this.provider = provider;
        this.cipher = cipher;
        this.outcomes = outcomes;
        this.refunds = refunds;
        this.refundOutcomes = refundOutcomes;
    }

    @Override
    public List<Payment> pendingCharges(int limit) {
        return payments.findByStatus(PaymentStatus.PENDING, limit);
    }

    @Override
    public void charge(Payment payment) {
        ProviderResult result;
        try {
            result = provider.charge(payment.idempotencyKey(), payment.total(),
                    cipher.decrypt(payment.encryptedPaymentToken()));
        } catch (PaymentProviderPort.ProviderTimeoutException e) {
            result = ProviderResult.unknown();         // the outcome is unknown: this is NOT a failure
        }
        outcomes.apply(payment, result);
    }

    @Override
    public List<Payment> unresolvedPayments(int limit) {
        return payments.findByStatus(PaymentStatus.UNKNOWN, limit);
    }

    @Override
    public void reconcile(Payment payment) {
        Optional<ProviderResult> found = provider.lookup(payment.idempotencyKey());
        if (found.isPresent() && found.get().isDefinitive()) {
            outcomes.apply(payment, found.get());
        }
    }

    @Override
    public long oldestUnresolvedSeconds() {
        return payments.oldestUnresolvedSeconds();
    }

    @Override
    public List<Refund> pendingRefunds(int limit) {
        return refunds.findByStatus(RefundStatus.PENDING, limit);
    }

    @Override
    public void refund(Refund refund) {
        ProviderResult result;
        try {
            result = provider.refund(refund.idempotencyKey(), refund.chargeIdempotencyKey(), refund.amount());
        } catch (PaymentProviderPort.ProviderTimeoutException e) {
            result = ProviderResult.unknown();         // the refund may have happened: reconcile, never re-key
        }
        refundOutcomes.apply(refund, result);
    }

    @Override
    public List<Refund> unresolvedRefunds(int limit) {
        return refunds.findByStatus(RefundStatus.UNKNOWN, limit);
    }

    @Override
    public void reconcileRefund(Refund refund) {
        Optional<ProviderResult> found = provider.lookupRefund(refund.idempotencyKey());
        if (found.isPresent() && found.get().isDefinitive()) {
            refundOutcomes.apply(refund, found.get());
        }
    }

    @Override
    public long oldestUnresolvedRefundSeconds() {
        return refunds.oldestUnresolvedSeconds();
    }
}

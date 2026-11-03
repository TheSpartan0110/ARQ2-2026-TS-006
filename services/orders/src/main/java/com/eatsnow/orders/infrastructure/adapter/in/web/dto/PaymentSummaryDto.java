package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.model.PaymentSummary;
import java.time.Instant;
import java.util.UUID;

/** PaymentSummary of Spec 3.4. Provider references and method tokens are never exposed. */
public record PaymentSummaryDto(UUID orderId, String status, Money total, Instant updatedAt) {
    public static PaymentSummaryDto fromDomain(PaymentSummary p) {
        return new PaymentSummaryDto(p.orderId(), p.status().name(), p.total(), p.updatedAt());
    }
}

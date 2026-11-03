package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import com.eatsnow.orders.domain.model.OrderReceipt;
import java.util.UUID;

public record OrderReceiptDto(UUID orderId, String status, UUID correlationId) {
    public static OrderReceiptDto fromDomain(OrderReceipt receipt) {
        return new OrderReceiptDto(receipt.orderId(), receipt.status(), receipt.correlationId());
    }
}

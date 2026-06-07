package com.ecommerce.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.agent.domain.PurchaseOrder;

public record PurchaseOrderResult(
        Long poId,
        Long supplierId,
        String status,
        BigDecimal totalCost,
        LocalDateTime createdAt,
        LocalDateTime receivedAt,
        LocalDateTime cancelledAt
) {

    public static PurchaseOrderResult from(PurchaseOrder po) {
        return new PurchaseOrderResult(
                po.getPoId(),
                po.getSupplierId(),
                po.getStatus(),
                po.getTotalCost(),
                po.getCreatedAt(),
                po.getReceivedAt(),
                po.getCancelledAt());
    }
}
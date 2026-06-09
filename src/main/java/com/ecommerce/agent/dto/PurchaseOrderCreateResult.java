package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record PurchaseOrderCreateResult(
        String status,
        Long poId,
        Long supplierId,
        String poStatus,
        BigDecimal totalCost,
        Integer itemCount,
        String approvalId,
        String message) {

    public static PurchaseOrderCreateResult created(
            Long poId,
            Long supplierId,
            BigDecimal totalCost,
            Integer itemCount,
            String approvalId) {
        return new PurchaseOrderCreateResult(
                "created",
                poId,
                supplierId,
                "placed",
                totalCost,
                itemCount,
                approvalId,
                "Purchase order created.");
    }
}

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

    public static PurchaseOrderCreateResult approvalRequired() {
        return new PurchaseOrderCreateResult(
                "approval_required",
                null,
                null,
                null,
                null,
                null,
                null,
                "purchase_order_create requires an approved approval_id. Call request_approval first.");
    }

    public static PurchaseOrderCreateResult invalidApproval(String approvalId) {
        return new PurchaseOrderCreateResult(
                "invalid_approval",
                null,
                null,
                null,
                null,
                null,
                approvalId,
                "approval_id is missing, not approved, stale, already consumed, or not bound to this operation.");
    }

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

package com.ecommerce.agent.dto;

public record PurchaseOrderReceiveResult(
        String status,
        Long poId,
        Integer itemCount,
        String approvalId,
        String message) {

    public static PurchaseOrderReceiveResult approvalRequired() {
        return new PurchaseOrderReceiveResult(
                "approval_required",
                null,
                null,
                null,
                "purchase_order_receive requires an approved approval_id. Call request_approval first.");
    }

    public static PurchaseOrderReceiveResult invalidApproval(String approvalId) {
        return new PurchaseOrderReceiveResult(
                "invalid_approval",
                null,
                null,
                approvalId,
                "approval_id is missing, not approved, stale, already consumed, or not bound to this operation.");
    }

    public static PurchaseOrderReceiveResult notReceivable(String approvalId, Long poId, String reason) {
        return new PurchaseOrderReceiveResult(
                "not_receivable",
                poId,
                null,
                approvalId,
                reason);
    }

    public static PurchaseOrderReceiveResult received(Long poId, Integer itemCount, String approvalId) {
        return new PurchaseOrderReceiveResult(
                "received",
                poId,
                itemCount,
                approvalId,
                "Purchase order received and inventory updated.");
    }
}

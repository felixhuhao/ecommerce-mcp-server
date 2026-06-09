package com.ecommerce.agent.dto;

public record PurchaseOrderReceiveResult(
        String status,
        Long poId,
        Integer itemCount,
        String approvalId,
        String message) {

    public static PurchaseOrderReceiveResult received(Long poId, Integer itemCount, String approvalId) {
        return new PurchaseOrderReceiveResult(
                "received",
                poId,
                itemCount,
                approvalId,
                "Purchase order received and inventory updated.");
    }
}

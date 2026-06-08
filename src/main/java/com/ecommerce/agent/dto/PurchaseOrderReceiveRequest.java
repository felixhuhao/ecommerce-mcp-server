package com.ecommerce.agent.dto;

public record PurchaseOrderReceiveRequest(
        String approvalId,
        Long poId,
        Long userId,
        String sessionId) {
}

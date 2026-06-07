package com.ecommerce.agent.dto;

import java.util.List;

public record PurchaseOrderCreateRequest(
        String approvalId,
        Long supplierId,
        List<PurchaseOrderCreateItemRequest> items,
        Long userId,
        String sessionId) {
}

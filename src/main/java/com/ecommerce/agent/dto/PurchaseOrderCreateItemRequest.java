package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record PurchaseOrderCreateItemRequest(
        Long productId,
        Integer quantity,
        BigDecimal unitCost) {
}

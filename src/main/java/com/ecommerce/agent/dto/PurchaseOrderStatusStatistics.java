package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record PurchaseOrderStatusStatistics(
        String status,
        Long purchaseOrderCount,
        BigDecimal totalCost) {
}

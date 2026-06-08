package com.ecommerce.agent.dto;

public record InventoryStatistics(
        Long productCount,
        Long lowStockCount,
        Long totalQuantity,
        Long totalSafetyStock) {
}

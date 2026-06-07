package com.ecommerce.agent.dto;

import java.time.LocalDateTime;

import com.ecommerce.agent.domain.Inventory;

public record InventoryLowStockResult(
        Long productId,
        Integer quantity,
        Integer safetyStock,
        Integer shortage,
        String warehouse,
        LocalDateTime updatedAt) {

    public static InventoryLowStockResult from(Inventory inventory) {
        return new InventoryLowStockResult(
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getSafetyStock(),
                inventory.getSafetyStock() - inventory.getQuantity(),
                inventory.getWarehouse(),
                inventory.getUpdatedAt());
    }
}
package com.ecommerce.agent.dto;

import java.time.LocalDateTime;

import com.ecommerce.agent.domain.Inventory;

public record InventoryResult(
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        Integer safetyStock,
        String warehouse,
        LocalDateTime updatedAt) {

    public static InventoryResult from(Inventory inventory) {
        return new InventoryResult(
                inventory.getProductId(),
                inventory.getSku(),
                inventory.getProductName(),
                inventory.getQuantity(),
                inventory.getSafetyStock(),
                inventory.getWarehouse(),
                inventory.getUpdatedAt());
    }
}

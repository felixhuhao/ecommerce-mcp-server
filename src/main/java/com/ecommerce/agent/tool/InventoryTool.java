package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.InventoryResult;
import com.ecommerce.agent.dto.InventoryLowStockResult;
import com.ecommerce.agent.service.InventoryService;

@Component
public class InventoryTool {

    private final InventoryService inventoryService;

    public InventoryTool(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @McpTool(
            name = "inventory_low_stock",
            description = "Read inventory items below safety stock. Use for low-stock, "
                    + "reorder-point, stockout-risk, or \"below safety stock\" questions. Returns "
                    + "productId, SKU, productName, quantity, safetyStock, shortage, warehouse, "
                    + "and updatedAt. Do not use to create purchase orders.")
    public List<InventoryLowStockResult> inventoryLowStock(
            @McpToolParam(required = false, description = "Maximum number of low-stock inventory "
                    + "rows to return, sorted by service-defined priority.") Integer limit) {
        return inventoryService.findLowStockItems(limit)
                .stream()
                .map(InventoryLowStockResult::from)
                .toList();
    }

    @McpTool(
            name = "inventory_query",
            description = "Read current inventory levels by productId and/or warehouse. Use after "
                    + "product_search resolves a SKU/name to productId, or when the operator gives "
                    + "productId directly. Returns SKU, productName, quantity, safetyStock, "
                    + "warehouse, and updatedAt. Not for historical sales or purchasing writes.")
    public List<InventoryResult> inventoryQuery(
            @McpToolParam(required = false, description = "Product id to filter inventory by. "
                    + "Resolve SKU/name with product_search before using this parameter.") Long productId,
            @McpToolParam(required = false, description = "Warehouse name to filter inventory by, "
                    + "for example a regional warehouse label.") String warehouse,
            @McpToolParam(required = false, description = "Maximum number of inventory rows to return.") Integer limit) {
        return inventoryService.queryInventory(productId, warehouse, limit)
                .stream()
                .map(InventoryResult::from)
                .toList();
    }
}

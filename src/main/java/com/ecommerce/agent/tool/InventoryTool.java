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

    @McpTool(name = "inventory_low_stock", description = "List inventory items whose quantity is below safety stock.")
    public List<InventoryLowStockResult> inventoryLowStock(
            @McpToolParam(required = false, description = "Maximum number of inventory items to return.") Integer limit) {
        return inventoryService.findLowStockItems(limit)
                .stream()
                .map(InventoryLowStockResult::from)
                .toList();
    }

    @McpTool(name = "inventory_query", description = "Query inventory levels by product id or warehouse.")
    public List<InventoryResult> inventoryQuery(
            @McpToolParam(required = false, description = "Product id to filter inventory by.") Long productId,
            @McpToolParam(required = false, description = "Warehouse name to filter inventory by.") String warehouse,
            @McpToolParam(required = false, description = "Maximum number of inventory items to return.") Integer limit) {
        return inventoryService.queryInventory(productId, warehouse, limit)
                .stream()
                .map(InventoryResult::from)
                .toList();
    }
}

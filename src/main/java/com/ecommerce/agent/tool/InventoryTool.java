package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.domain.Inventory;
import com.ecommerce.agent.service.InventoryService;

@Component
public class InventoryTool {

    private final InventoryService inventoryService;

    public InventoryTool(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @McpTool(name = "inventory_low_stock", description = "List inventory items whose quantity is below safety stock.")
    public List<Inventory> inventoryLowStock(
            @McpToolParam(required = false, description = "Maximum number of inventory items to return.") Integer limit) {
        return inventoryService.findLowStockItems(limit);
    }
}
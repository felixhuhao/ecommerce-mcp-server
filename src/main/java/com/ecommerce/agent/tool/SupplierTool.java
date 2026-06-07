package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.domain.Supplier;
import com.ecommerce.agent.service.SupplierService;

@Component
public class SupplierTool {

    private final SupplierService supplierService;

    public SupplierTool(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @McpTool(name = "supplier_top", description = "List top suppliers ordered by rating and lead time.")
    public List<Supplier> supplierTop(
            @McpToolParam(required = false, description = "Maximum number of suppliers to return.") Integer limit) {
        return supplierService.findTopSuppliers(limit);
    }

    @McpTool(name = "supplier_search", description = "Search suppliers by supplier name or contact person.")
    public List<Supplier> supplierSearch(
            @McpToolParam(description = "Supplier name or contact person keyword.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of suppliers to return.") Integer limit) {
        return supplierService.searchSuppliers(keyword, limit);
    }
}
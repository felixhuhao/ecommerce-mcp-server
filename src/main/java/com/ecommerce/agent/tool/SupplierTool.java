package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.SupplierResult;
import com.ecommerce.agent.service.SupplierService;

@Component
public class SupplierTool {

    private final SupplierService supplierService;

    public SupplierTool(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @McpTool(
            name = "supplier_top",
            description = "Read top suppliers ordered by rating and lead-time quality. Use for "
                    + "supplier comparison, sourcing options, or procurement context before "
                    + "proposing a purchase order. Does not create or receive purchase orders.")
    public List<SupplierResult> supplierTop(
            @McpToolParam(required = false, description = "Maximum number of ranked supplier rows to return.") Integer limit) {
        return supplierService.findTopSuppliers(limit)
                .stream()
                .map(SupplierResult::from)
                .toList();
    }

    @McpTool(
            name = "supplier_query",
            description = "Read supplier records by supplier name or contact person. Use to "
                    + "confirm supplierId, name, contact details, rating, and leadTime before a "
                    + "procurement proposal. Do not use for customer orders or inventory quantities.")
    public List<SupplierResult> supplierQuery(
            @McpToolParam(required = false, description = "Supplier name or contact-person keyword to search.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of matching suppliers to return.") Integer limit) {
        return supplierService.searchSuppliers(keyword, limit)
                .stream()
                .map(SupplierResult::from)
                .toList();
    }
}

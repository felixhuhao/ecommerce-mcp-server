package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateResult;
import com.ecommerce.agent.dto.PurchaseOrderResult;
import com.ecommerce.agent.service.PurchaseOrderService;

@Component
public class PurchaseOrderTool {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderTool(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @McpTool(name = "purchase_order_query", description = "Query recent supplier purchase orders")
    public List<PurchaseOrderResult> purchaseOrderQuery(
            @McpToolParam(required = false, description = "Maximum number of purchase orders to return.") Integer limit) {
        return purchaseOrderService.findRecentPurchaseOrders(limit)
                .stream()
                .map(PurchaseOrderResult::from)
                .toList();
    }

    @McpTool(name = "purchase_order_create", description = "Create a supplier purchase order after human approval.")
    public PurchaseOrderCreateResult purchaseOrderCreate(
            @McpToolParam(required = false, description = "Approval id returned by request_approval.") String approvalId,
            @McpToolParam(description = "Supplier id for the purchase order.") Long supplierId,
            @McpToolParam(description = "Purchase order line items.") List<PurchaseOrderCreateItemRequest> items,
            @McpToolParam(description = "Authenticated user id for this write operation.") Long userId,
            @McpToolParam(description = "Authenticated session id for this write operation.") String sessionId) {
        return purchaseOrderService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                approvalId,
                supplierId,
                items,
                userId,
                sessionId));
    }
}

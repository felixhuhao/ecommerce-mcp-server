package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.PurchaseOrderResult;
import com.ecommerce.agent.service.PurchaseOrderService;

@Component
public class PurchaseOrderTool {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderTool(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @McpTool(
            name = "purchase_order_query",
            description = "Read recent supplier purchase orders and their status. Use for "
                    + "checking existing POs, receiving context, or avoiding duplicate "
                    + "procurement proposals. This is read-only and does not create or receive "
                    + "purchase orders.")
    public List<PurchaseOrderResult> purchaseOrderQuery(
            @McpToolParam(required = false, description = "Maximum number of recent purchase-order rows to return.") Integer limit) {
        return purchaseOrderService.findRecentPurchaseOrders(limit)
                .stream()
                .map(PurchaseOrderResult::from)
                .toList();
    }
}

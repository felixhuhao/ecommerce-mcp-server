package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.CustomerOrderResult;
import com.ecommerce.agent.service.CustomerOrderService;

@Component
public class CustomerOrderTool {

    private final CustomerOrderService customerOrderService;

    public CustomerOrderTool(CustomerOrderService customerOrderService) {
        this.customerOrderService = customerOrderService;
    }

    @McpTool(
            name = "order_query",
            description = "Read customer sales orders with line items. Use for a specific "
                    + "customer's order history, order-status checks, or detailed order rows for "
                    + "analysis. Filter by orderId, userId, or status when available. Do not use to "
                    + "recompute top-level customer spend or category-sales aggregates when "
                    + "aggregate tools can answer.")
    public List<CustomerOrderResult> orderQuery(
            @McpToolParam(required = false, description = "Customer order id to look up one specific order.") Long orderId,
            @McpToolParam(required = false, description = "Customer user id to return orders for one customer.") Long userId,
            @McpToolParam(required = false, description = "Order status filter, such as pending, "
                    + "paid, shipped, completed, or cancelled.") String status,
            @McpToolParam(required = false, description = "Maximum number of recent orders to return.") Integer limit,
            @McpToolParam(required = false, description = "For monitoring stale orders only: "
                    + "when status is pending, return orders whose createdAt is at least this many hours old; "
                    + "when status is paid, return orders whose paidAt is at least this many hours old. "
                    + "Stale results are ordered oldest first.") Integer staleOlderThanHours) {
        return customerOrderService.queryOrders(orderId, userId, status, limit, staleOlderThanHours)
                .stream()
                .map(CustomerOrderResult::from)
                .toList();
    }
}

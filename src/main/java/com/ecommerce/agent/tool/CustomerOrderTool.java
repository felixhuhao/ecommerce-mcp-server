package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.CustomerOrderResult;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.OrderUpdateResult;
import com.ecommerce.agent.service.CustomerOrderService;

@Component
public class CustomerOrderTool {

    private final CustomerOrderService customerOrderService;

    public CustomerOrderTool(CustomerOrderService customerOrderService) {
        this.customerOrderService = customerOrderService;
    }

    @McpTool(name = "order_query", description = "Query customer sales orders with line items.")
    public List<CustomerOrderResult> orderQuery(
            @McpToolParam(required = false, description = "Customer user id.") Long userId,
            @McpToolParam(required = false, description = "Order status.") String status,
            @McpToolParam(required = false, description = "Maximum number of orders to return.") Integer limit) {
        return customerOrderService.queryOrders(userId, status, limit)
                .stream()
                .map(CustomerOrderResult::from)
                .toList();
    }

    @McpTool(name = "order_update", description = "Update a customer order fulfillment status after human approval.")
    public OrderUpdateResult orderUpdate(
            @McpToolParam(required = false, description = "Approval id returned by request_approval.") String approvalId,
            @McpToolParam(description = "Customer order id to update.") Long orderId,
            @McpToolParam(description = "New order status: shipped, completed, or cancelled.") String newStatus,
            @McpToolParam(description = "Authenticated user id for this write operation.") Long userId,
            @McpToolParam(description = "Authenticated session id for this write operation.") String sessionId) {
        return customerOrderService.updateOrder(new OrderUpdateRequest(
                approvalId,
                orderId,
                newStatus,
                userId,
                sessionId));
    }
}

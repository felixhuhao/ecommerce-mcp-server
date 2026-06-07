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
}

package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.CustomerResult;
import com.ecommerce.agent.service.CustomerService;

@Component
public class CustomerTool {

    private final CustomerService customerService;

    public CustomerTool(CustomerService customerService) {
        this.customerService = customerService;
    }

    @McpTool(name = "user_query", description = "Query customer accounts by user id, keyword, level, or recent registration.")
    public List<CustomerResult> userQuery(
            @McpToolParam(required = false, description = "Customer user id.") Long userId,
            @McpToolParam(required = false, description = "Username, email, or phone keyword.") String keyword,
            @McpToolParam(required = false, description = "Customer level from 1 to 4.") Integer level,
            @McpToolParam(required = false, description = "Maximum number of customers to return.") Integer limit) {
        return customerService.queryCustomers(userId, keyword, level, limit)
                .stream()
                .map(CustomerResult::from)
                .toList();
    }
}

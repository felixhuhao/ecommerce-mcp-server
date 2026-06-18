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

    @McpTool(
            name = "user_query",
            description = "Read customer account/profile rows. Use to resolve a customer userId, "
                    + "inspect profile fields, or clarify labels for a small set of customers. "
                    + "Do not use as a substitute for aggregate customer-spend summaries.")
    public List<CustomerResult> userQuery(
            @McpToolParam(required = false, description = "Customer user id for an exact customer lookup.") Long userId,
            @McpToolParam(required = false, description = "Username, email, or phone keyword for "
                    + "customer search.") String keyword,
            @McpToolParam(required = false, description = "Customer level from 1 to 4, if segment "
                    + "filtering is needed.") Integer level,
            @McpToolParam(required = false, description = "Maximum number of customer rows to return.") Integer limit) {
        return customerService.queryCustomers(userId, keyword, level, limit)
                .stream()
                .map(CustomerResult::from)
                .toList();
    }
}

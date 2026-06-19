package com.ecommerce.agent.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.StatisticsResult;
import com.ecommerce.agent.service.StatsService;

@Component
public class StatisticsTool {

    private final StatsService statsService;

    public StatisticsTool(StatsService statsService) {
        this.statsService = statsService;
    }

    @McpTool(
            name = "get_statistics",
            description = "Read broad backend-computed ecommerce aggregates. Use for miscellaneous "
                    + "operational summaries such as inventory health, orders by status, products "
                    + "by category, salesByCategory, salesDropWow, purchase orders by status, "
                    + "topProductsByRevenue, and topCustomersBySpend. Prefer narrower "
                    + "agent-facing wrapper tools when available for a specific aggregate.")
    public StatisticsResult getStatistics(
            @McpToolParam(required = false, description = "Maximum number of top products and top "
                    + "customers to include in the aggregate response. Other aggregate sections "
                    + "use service defaults.") Integer topProductLimit) {
        return statsService.getStatistics(topProductLimit);
    }
}

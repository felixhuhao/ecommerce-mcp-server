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

    @McpTool(name = "get_statistics", description = "Get aggregate ecommerce statistics for analysis.")
    public StatisticsResult getStatistics(
            @McpToolParam(required = false, description = "Maximum number of top products to return.") Integer topProductLimit) {
        return statsService.getStatistics(topProductLimit);
    }
}

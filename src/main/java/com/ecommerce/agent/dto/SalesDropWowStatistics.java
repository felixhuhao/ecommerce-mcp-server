package com.ecommerce.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesDropWowStatistics(
        String category,
        BigDecimal currentSales,
        BigDecimal previousSales,
        BigDecimal dropPct,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDate previousPeriodStart,
        LocalDate previousPeriodEnd) {
}

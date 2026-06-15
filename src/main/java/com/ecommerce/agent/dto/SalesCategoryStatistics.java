package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record SalesCategoryStatistics(
        String category,
        Long unitsSold,
        BigDecimal totalSales) {
}

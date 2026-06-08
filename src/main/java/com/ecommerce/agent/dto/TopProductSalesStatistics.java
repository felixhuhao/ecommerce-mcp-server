package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record TopProductSalesStatistics(
        Long productId,
        String productName,
        Long unitsSold,
        BigDecimal revenue) {
}

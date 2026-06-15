package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record TopCustomerSpendStatistics(
        Long userId,
        String username,
        Long orderCount,
        BigDecimal totalSpend) {
}

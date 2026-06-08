package com.ecommerce.agent.dto;

import java.math.BigDecimal;

public record OrderStatusStatistics(
        String status,
        Long orderCount,
        BigDecimal totalAmount) {
}

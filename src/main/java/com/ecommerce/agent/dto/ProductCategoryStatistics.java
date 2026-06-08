package com.ecommerce.agent.dto;

public record ProductCategoryStatistics(
        String category,
        Long productCount,
        Long activeProductCount,
        Long inactiveProductCount) {
}

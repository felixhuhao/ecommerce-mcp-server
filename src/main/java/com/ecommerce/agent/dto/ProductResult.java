package com.ecommerce.agent.dto;

import java.math.BigDecimal;

import com.ecommerce.agent.domain.Product;

public record ProductResult(
        Long productId,
        String sku,
        String name,
        String category,
        BigDecimal price,
        BigDecimal cost,
        String status) {

    public static ProductResult from(Product product) {
        return new ProductResult(
                product.getProductId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getCost(),
                product.getStatus());
    }
}

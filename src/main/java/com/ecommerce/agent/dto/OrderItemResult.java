package com.ecommerce.agent.dto;

import java.math.BigDecimal;

import com.ecommerce.agent.domain.OrderItem;

public record OrderItemResult(
        Long itemId,
        Long orderId,
        Long productId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public static OrderItemResult from(OrderItem item) {
        return new OrderItemResult(
                item.getItemId(),
                item.getOrderId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal());
    }
}

package com.ecommerce.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.service.CustomerOrderService.CustomerOrderWithItems;

public record CustomerOrderResult(
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        List<OrderItemResult> items) {

    public static CustomerOrderResult from(CustomerOrderWithItems orderWithItems) {
        CustomerOrder order = orderWithItems.order();

        return new CustomerOrderResult(
                order.getOrderId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaidAt(),
                orderWithItems.items()
                        .stream()
                        .map(OrderItemResult::from)
                        .toList());
    }
}

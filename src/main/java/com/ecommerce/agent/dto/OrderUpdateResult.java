package com.ecommerce.agent.dto;

public record OrderUpdateResult(
        String status,
        Long orderId,
        String previousStatus,
        String newStatus,
        String approvalId,
        String message) {

    public static OrderUpdateResult updated(
            Long orderId,
            String previousStatus,
            String newStatus,
            String approvalId) {
        return new OrderUpdateResult(
                "updated",
                orderId,
                previousStatus,
                newStatus,
                approvalId,
                "Customer order status updated.");
    }
}

package com.ecommerce.agent.dto;

public record OrderUpdateRequest(
        String approvalId,
        Long orderId,
        String newStatus,
        Long userId,
        String sessionId) {
}

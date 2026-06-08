package com.ecommerce.agent.dto;

public record OrderUpdateResult(
        String status,
        Long orderId,
        String previousStatus,
        String newStatus,
        String approvalId,
        String message) {

    public static OrderUpdateResult approvalRequired() {
        return new OrderUpdateResult(
                "approval_required",
                null,
                null,
                null,
                null,
                "order_update requires an approved approval_id. Call request_approval first.");
    }

    public static OrderUpdateResult invalidApproval(String approvalId) {
        return new OrderUpdateResult(
                "invalid_approval",
                null,
                null,
                null,
                approvalId,
                "approval_id is missing, not approved, stale, already consumed, or not bound to this operation.");
    }

    public static OrderUpdateResult notUpdatable(String approvalId, Long orderId, String reason) {
        return new OrderUpdateResult(
                "not_updatable",
                orderId,
                null,
                null,
                approvalId,
                reason);
    }

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

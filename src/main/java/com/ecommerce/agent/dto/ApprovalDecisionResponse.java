package com.ecommerce.agent.dto;

public record ApprovalDecisionResponse(
        String approvalId,
        String status,
        boolean changed,
        String rejectionReason) {
}

package com.ecommerce.agent.dto;

public record ApprovalExecutionResponse(
        String approvalId,
        String status,
        String executionResult,
        String message) {
}

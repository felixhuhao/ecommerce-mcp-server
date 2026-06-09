package com.ecommerce.agent.dto;

import tools.jackson.databind.JsonNode;

public record ApprovalExecutionResponse(
        String approvalId,
        String status,
        JsonNode executionResult,
        String message,
        String reasonCode,
        Boolean retryable) {

    public ApprovalExecutionResponse(
            String approvalId,
            String status,
            JsonNode executionResult,
            String message) {
        this(approvalId, status, executionResult, message, null, null);
    }

    public static ApprovalExecutionResponse retryableInfrastructureError(String approvalId) {
        return new ApprovalExecutionResponse(
                approvalId,
                "approved",
                null,
                "approval execution hit a database error; retry the same approval",
                "infrastructure_error",
                true);
    }
}

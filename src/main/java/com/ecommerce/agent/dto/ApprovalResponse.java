package com.ecommerce.agent.dto;

import com.ecommerce.agent.domain.ApprovalRecord;

public record ApprovalResponse(
        String approvalId,
        String operationHash,
        String toolName,
        String operationType,
        String operationPayload,
        String operationDetail,
        Long userId,
        String sessionId,
        String status,
        String rejectionReason,
        String executionResult) {

    public static ApprovalResponse from(ApprovalRecord approvalRecord) {
        return new ApprovalResponse(
                approvalRecord.getApprovalId(),
                approvalRecord.getOperationHash(),
                approvalRecord.getToolName(),
                approvalRecord.getOperationType(),
                approvalRecord.getOperationPayload(),
                approvalRecord.getOperationDetail(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId(),
                approvalRecord.getStatus(),
                approvalRecord.getRejectionReason(),
                approvalRecord.getExecutionResult());
    }
}

package com.ecommerce.agent.tool;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.ApprovalResponse;
import com.ecommerce.agent.service.ApprovalService;

@Component
public class ApprovalTool {

    private final ApprovalService approvalService;
    private final ApprovalPayloadBuilder approvalPayloadBuilder;

    public ApprovalTool(ApprovalService approvalService, ApprovalPayloadBuilder approvalPayloadBuilder) {
        this.approvalService = approvalService;
        this.approvalPayloadBuilder = approvalPayloadBuilder;
    }

    @McpTool(name = "request_approval", description = "Request human approval for a structured write operation.")
    public ApprovalResponse requestApproval(
            @McpToolParam(description = "Write tool name that this approval authorizes.") String toolName,
            @McpToolParam(description = "Operation type, such as create, update, or receive.") String operationType,
            @McpToolParam(description = "Structured operation parameters. The Agent must not provide approval text.") Map<String, Object> operationParams,
            @McpToolParam(description = "Authenticated user id for this approval request.") Long userId,
            @McpToolParam(description = "Authenticated session id for this approval request.") String sessionId) {
        ApprovalRequest request = new ApprovalRequest(toolName, operationType, operationParams, userId, sessionId);
        approvalPayloadBuilder.validateSupportedRequest(request);

        ApprovalRecord approvalRecord = approvalService.createPending(
                request.toolName(),
                request.operationType(),
                approvalPayloadBuilder.operationPayloadJson(request),
                approvalPayloadBuilder.operationDetailJson(request),
                request.userId(),
                request.sessionId());
        return ApprovalResponse.from(approvalRecord);
    }
}

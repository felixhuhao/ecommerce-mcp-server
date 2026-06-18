package com.ecommerce.agent.tool;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.auth.TrustedActor;
import com.ecommerce.agent.auth.TrustedActorContext;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.ApprovalResponse;
import com.ecommerce.agent.service.ApprovalService;

@Component
public class ApprovalTool {

    private final ApprovalService approvalService;
    private final ApprovalPayloadBuilder approvalPayloadBuilder;
    private final TrustedActorContext trustedActorContext;

    public ApprovalTool(
            ApprovalService approvalService,
            ApprovalPayloadBuilder approvalPayloadBuilder,
            TrustedActorContext trustedActorContext) {
        this.approvalService = approvalService;
        this.approvalPayloadBuilder = approvalPayloadBuilder;
        this.trustedActorContext = trustedActorContext;
    }

    @McpTool(
            name = "request_approval",
            description = "Create a pending human-approval record for a supported structured "
                    + "write. Use only after read tools have confirmed the facts needed for "
                    + "operationParams. This tool does not execute the write; approval and "
                    + "execution happen later through the REST approval flow.")
    public ApprovalResponse requestApproval(
            @McpToolParam(description = "Supported write tool name this approval authorizes, such "
                    + "as order_update, purchase_order_create, or purchase_order_receive.") String toolName,
            @McpToolParam(description = "Operation type for the write, such as create, update, or receive.") String operationType,
            @McpToolParam(description = "Structured operation parameters only. Do not include "
                    + "approval text, user/session identity, or fields not required by the target "
                    + "write contract.") Map<String, Object> operationParams) {
        TrustedActor actor = trustedActorContext.requireCurrentActor();
        ApprovalRequest request = new ApprovalRequest(toolName, operationType, operationParams, actor.userId(), actor.sessionId());
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

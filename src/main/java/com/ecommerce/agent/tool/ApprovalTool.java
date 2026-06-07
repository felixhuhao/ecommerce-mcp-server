package com.ecommerce.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.ApprovalResponse;
import com.ecommerce.agent.service.ApprovalService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApprovalTool {

    private static final Set<String> SUPPORTED_WRITE_TOOLS = Set.of("purchase_order_create");

    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;

    public ApprovalTool(ApprovalService approvalService, ObjectMapper objectMapper) {
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "request_approval", description = "Request human approval for a structured write operation.")
    public ApprovalResponse requestApproval(
            @McpToolParam(description = "Write tool name that this approval authorizes.") String toolName,
            @McpToolParam(description = "Operation type, such as create, update, or receive.") String operationType,
            @McpToolParam(description = "Structured operation parameters. The Agent must not provide approval text.") Map<String, Object> operationParams,
            @McpToolParam(description = "Authenticated user id for this approval request.") Long userId,
            @McpToolParam(description = "Authenticated session id for this approval request.") String sessionId) {
        ApprovalRequest request = new ApprovalRequest(toolName, operationType, operationParams, userId, sessionId);
        validateSupportedRequest(request);

        ApprovalRecord approvalRecord = approvalService.createPending(
                request.toolName(),
                request.operationType(),
                toJson(operationPayload(request)),
                toJson(operationDetail(request)),
                request.userId(),
                request.sessionId());
        return ApprovalResponse.from(approvalRecord);
    }

    private void validateSupportedRequest(ApprovalRequest request) {
        if (request.toolName() == null || !SUPPORTED_WRITE_TOOLS.contains(request.toolName())) {
            throw new IllegalArgumentException("unsupported approval toolName: " + request.toolName());
        }
        if (request.operationParams() == null || request.operationParams().isEmpty()) {
            throw new IllegalArgumentException("operationParams must not be empty");
        }
    }

    private Map<String, Object> operationPayload(ApprovalRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", request.operationParams());
        return payload;
    }

    private Map<String, Object> operationDetail(ApprovalRequest request) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", request.operationParams());
        return detail;
    }

    private String titleFor(String toolName) {
        if ("purchase_order_create".equals(toolName)) {
            return "Create purchase order";
        }

        return "Approve write operation";
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("operation parameters must be JSON serializable", e);
        }
    }
}

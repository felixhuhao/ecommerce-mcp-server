package com.ecommerce.agent.dto;

import java.util.Map;

public record ApprovalRequest(
        String toolName,
        String operationType,
        Map<String, Object> operationParams,
        Long userId,
        String sessionId) {
}
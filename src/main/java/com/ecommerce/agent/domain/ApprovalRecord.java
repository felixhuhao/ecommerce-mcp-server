package com.ecommerce.agent.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ApprovalRecord {
    private String approvalId;
    private String operationHash;
    private String toolName;
    private String operationType;
    private String operationPayload;
    private String operationDetail;
    private Long userId;
    private String sessionId;
    private String status;
    private String rejectionReason;
    private String executionResult;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime consumedAt;
    private LocalDateTime executedAt;
}

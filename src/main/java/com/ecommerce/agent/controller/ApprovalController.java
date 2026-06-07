package com.ecommerce.agent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalDecisionResponse;
import com.ecommerce.agent.dto.ApprovalRejectRequest;
import com.ecommerce.agent.dto.ApprovalResponse;
import com.ecommerce.agent.service.ApprovalService;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String SESSION_ID_HEADER = "X-Session-Id";

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/{approvalId}")
    public ResponseEntity<ApprovalResponse> findById(
            @PathVariable String approvalId,
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId) {
        return approvalService.findById(approvalId)
                .filter(approvalRecord -> isSameActor(approvalRecord, userId, sessionId))
                .map(ApprovalResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{approvalId}/approve")
    public ResponseEntity<ApprovalDecisionResponse> approve(
            @PathVariable String approvalId,
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId) {
        boolean changed = approvalService.approve(approvalId, userId, sessionId);
        return decisionResponse(approvalId, "approved", changed);
    }

    @PostMapping("/{approvalId}/reject")
    public ResponseEntity<ApprovalDecisionResponse> reject(
            @PathVariable String approvalId,
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestBody(required = false) ApprovalRejectRequest request) {
        boolean changed = approvalService.reject(approvalId, userId, sessionId);
        return decisionResponse(approvalId, "rejected", changed);
    }

    private ResponseEntity<ApprovalDecisionResponse> decisionResponse(
            String approvalId,
            String status,
            boolean changed) {
        ApprovalDecisionResponse response = new ApprovalDecisionResponse(approvalId, status, changed);
        if (changed) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    private boolean isSameActor(ApprovalRecord approvalRecord, Long userId, String sessionId) {
        return approvalRecord.getUserId().equals(userId)
                && approvalRecord.getSessionId().equals(sessionId);
    }
}

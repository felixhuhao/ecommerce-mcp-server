package com.ecommerce.agent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.agent.auth.TrustedActor;
import com.ecommerce.agent.auth.TrustedActorContext;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalDecisionResponse;
import com.ecommerce.agent.dto.ApprovalRejectRequest;
import com.ecommerce.agent.dto.ApprovalResponse;
import com.ecommerce.agent.service.ApprovalService;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final TrustedActorContext trustedActorContext;

    public ApprovalController(ApprovalService approvalService, TrustedActorContext trustedActorContext) {
        this.approvalService = approvalService;
        this.trustedActorContext = trustedActorContext;
    }

    @GetMapping("/{approvalId}")
    public ResponseEntity<ApprovalResponse> findById(@PathVariable String approvalId) {
        TrustedActor actor = trustedActorContext.requireCurrentActor();
        return approvalService.findById(approvalId)
                .filter(approvalRecord -> isSameActor(approvalRecord, actor))
                .map(ApprovalResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{approvalId}/approve")
    public ResponseEntity<ApprovalDecisionResponse> approve(@PathVariable String approvalId) {
        TrustedActor actor = trustedActorContext.requireCurrentActor();
        boolean changed = approvalService.approve(approvalId, actor.userId(), actor.sessionId());
        return decisionResponse(approvalId, "approved", changed);
    }

    @PostMapping("/{approvalId}/reject")
    public ResponseEntity<ApprovalDecisionResponse> reject(
            @PathVariable String approvalId,
            @RequestBody(required = false) ApprovalRejectRequest request) {
        TrustedActor actor = trustedActorContext.requireCurrentActor();
        boolean changed = approvalService.reject(approvalId, actor.userId(), actor.sessionId());
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

    private boolean isSameActor(ApprovalRecord approvalRecord, TrustedActor actor) {
        return approvalRecord.getUserId().equals(actor.userId())
                && approvalRecord.getSessionId().equals(actor.sessionId());
    }
}

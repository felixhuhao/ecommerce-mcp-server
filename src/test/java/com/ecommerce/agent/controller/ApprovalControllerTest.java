package com.ecommerce.agent.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.service.ApprovalService;

@SpringBootTest(properties = "app.auth.service-token=test-service-token")
@AutoConfigureMockMvc
@Transactional
class ApprovalControllerTest {

    private static final String SERVICE_TOKEN = "test-service-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

    @Test
    void findByIdReturnsApprovalForBoundActorAndSession() throws Exception {
        ApprovalRecord approvalRecord = createPendingApproval("test-session");

        mockMvc.perform(get("/approvals/{approvalId}", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approvalRecord.getApprovalId()))
                .andExpect(jsonPath("$.toolName").value("purchase_order_create"))
                .andExpect(jsonPath("$.operationDetail", containsString("Create purchase order")))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void findByIdRejectsDifferentSession() throws Exception {
        ApprovalRecord approvalRecord = createPendingApproval("test-session");

        mockMvc.perform(get("/approvals/{approvalId}", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "other-session"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveTransitionsPendingApproval() throws Exception {
        ApprovalRecord approvalRecord = createPendingApproval("test-session");

        mockMvc.perform(post("/approvals/{approvalId}/approve", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approvalRecord.getApprovalId()))
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    void rejectTransitionsPendingApproval() throws Exception {
        ApprovalRecord approvalRecord = createPendingApproval("test-session");

        mockMvc.perform(post("/approvals/{approvalId}/reject", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  Not needed  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approvalRecord.getApprovalId()))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.rejectionReason").value("Not needed"));

        mockMvc.perform(post("/approvals/{approvalId}/reject", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Second reason\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.rejectionReason").value(nullValue()));

        mockMvc.perform(get("/approvals/{approvalId}", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectionReason").value("Not needed"));
    }

    @Test
    void approveReturnsConflictWhenApprovalCannotTransition() throws Exception {
        ApprovalRecord approvalRecord = createPendingApproval("test-session");

        mockMvc.perform(post("/approvals/{approvalId}/approve", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "other-session"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void executeApprovedApprovalRunsStoredPayload() throws Exception {
        ApprovalRecord approvalRecord = createPendingPurchaseOrderCreateApproval("test-session");
        approvalService.approve(approvalRecord.getApprovalId(), 1L, "test-session");

        mockMvc.perform(post("/approvals/{approvalId}/execute", approvalRecord.getApprovalId())
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approvalRecord.getApprovalId()))
                .andExpect(jsonPath("$.status").value("consumed"))
                .andExpect(jsonPath("$.executionResult.status").value("created"))
                .andExpect(jsonPath("$.executionResult.poId").isNumber())
                .andExpect(jsonPath("$.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.retryable").doesNotExist());
    }

    @Test
    void approveRejectsMissingServiceToken() throws Exception {
        ApprovalRecord approvalRecord = createPendingApproval("test-session");

        mockMvc.perform(post("/approvals/{approvalId}/approve", approvalRecord.getApprovalId())
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session"))
                .andExpect(status().isUnauthorized());
    }

    private ApprovalRecord createPendingApproval(String sessionId) {
        return approvalService.createPending(
                "purchase_order_create",
                "create",
                "{\"productId\":1,\"quantity\":10}",
                "{\"title\":\"Create purchase order\"}",
                1L,
                sessionId);
    }

    private ApprovalRecord createPendingPurchaseOrderCreateApproval(String sessionId) {
        ApprovalRequest approvalRequest = new ApprovalRequest(
                "purchase_order_create",
                "create",
                Map.of(
                        "supplierId", 1,
                        "items", List.of(Map.of(
                                "productId", 2,
                                "quantity", 10,
                                "unitCost", new BigDecimal("120.00")))),
                1L,
                sessionId);
        return approvalService.createPending(
                approvalRequest.toolName(),
                approvalRequest.operationType(),
                approvalPayloadBuilder.operationPayloadJson(approvalRequest),
                approvalPayloadBuilder.operationDetailJson(approvalRequest),
                approvalRequest.userId(),
                approvalRequest.sessionId());
    }
}

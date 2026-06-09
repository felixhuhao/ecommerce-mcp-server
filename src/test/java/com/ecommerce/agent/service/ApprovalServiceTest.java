package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.mapper.ApprovalRecordMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class ApprovalServiceTest {

    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPendingCanonicalizesPayloadAndHashesIt() throws JacksonException {
        ApprovalRecord first = approvalService.createPending(
                "purchase_order_create",
                "create",
                "{\"quantity\":10,\"productId\":1}",
                "{\"title\":\"Create purchase order\"}",
                1L,
                "test-session");
        ApprovalRecord second = approvalService.createPending(
                "purchase_order_create",
                "create",
                "{\"productId\":1,\"quantity\":10}",
                "{\"title\":\"Create purchase order\"}",
                1L,
                "test-session");

        assertThat(first.getApprovalId()).isNotBlank();
        assertThat(first.getStatus()).isEqualTo("pending");
        assertThat(json(first.getOperationPayload()))
                .isEqualTo(json("{\"productId\":1,\"quantity\":10}"));
        assertThat(first.getOperationHash()).hasSize(64);
        assertThat(second.getOperationHash()).isEqualTo(first.getOperationHash());
    }

    @Test
    void approveRequiresSameActorAndSession() {
        ApprovalRecord approvalRecord = approvalService.createPending(
                "purchase_order_create",
                "create",
                "{\"productId\":1,\"quantity\":10}",
                "{\"title\":\"Create purchase order\"}",
                1L,
                "test-session");

        boolean wrongSessionApproved = approvalService.approve(
                approvalRecord.getApprovalId(),
                1L,
                "other-session");
        boolean approved = approvalService.approve(
                approvalRecord.getApprovalId(),
                1L,
                "test-session");

        assertThat(wrongSessionApproved).isFalse();
        assertThat(approved).isTrue();
        assertThat(approvalRecordMapper.findById(approvalRecord.getApprovalId()).getStatus())
                .isEqualTo("approved");
    }

    @Test
    void createPendingRejectsInvalidJson() {
        assertThatThrownBy(() -> approvalService.createPending(
                "purchase_order_create",
                "create",
                "{invalid-json}",
                "{\"title\":\"Create purchase order\"}",
                1L,
                "test-session"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JSON must be valid");
    }

    @Test
    void findByIdLazilyExpiresOpenApproval() {
        ApprovalRecord approvalRecord = newApprovalRecord("pending", EXPIRED_AT);
        approvalRecordMapper.insert(approvalRecord);

        ApprovalRecord found = approvalService.findById(approvalRecord.getApprovalId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo("expired");
    }

    @Test
    void approveRejectsExpiredApprovalsAndMarkThemExpired() {
        ApprovalRecord pendingApproval = newApprovalRecord("pending", EXPIRED_AT);
        approvalRecordMapper.insert(pendingApproval);
        ApprovalRecord approvedApproval = newApprovalRecord("approved", EXPIRED_AT);
        approvalRecordMapper.insert(approvedApproval);

        boolean approved = approvalService.approve(pendingApproval.getApprovalId(), 1L, "test-session");
        approvalService.findById(approvedApproval.getApprovalId());

        assertThat(approved).isFalse();
        assertThat(approvalRecordMapper.findById(pendingApproval.getApprovalId()).getStatus())
                .isEqualTo("expired");
        assertThat(approvalRecordMapper.findById(approvedApproval.getApprovalId()).getStatus())
                .isEqualTo("expired");
    }

    private ApprovalRecord newApprovalRecord(String status, LocalDateTime expiresAt) {
        ApprovalRecord approvalRecord = new ApprovalRecord();
        approvalRecord.setApprovalId(UUID.randomUUID().toString());
        approvalRecord.setOperationHash("f".repeat(64));
        approvalRecord.setToolName("purchase_order_create");
        approvalRecord.setOperationType("create");
        approvalRecord.setOperationPayload("{\"productId\":1,\"quantity\":10}");
        approvalRecord.setOperationDetail("{\"title\":\"Create purchase order\"}");
        approvalRecord.setUserId(1L);
        approvalRecord.setSessionId("test-session");
        approvalRecord.setStatus(status);
        approvalRecord.setExpiresAt(expiresAt);
        return approvalRecord;
    }

    private JsonNode json(String value) throws JacksonException {
        return objectMapper.readTree(value);
    }
}

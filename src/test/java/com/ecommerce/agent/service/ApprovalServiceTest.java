package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.ApprovalRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class ApprovalServiceTest {

    @Autowired
    private ApprovalService approvalService;

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
    void approveAndConsumeRequiresSameActorSessionToolAndPayload() {
        ApprovalRecord approvalRecord = approvalService.createPending(
                "purchase_order_create",
                "create",
                "{\"productId\":1,\"quantity\":10}",
                "{\"title\":\"Create purchase order\"}",
                1L,
                "test-session");

        boolean approved = approvalService.approve(approvalRecord.getApprovalId(), 1L, "test-session");
        boolean wrongPayloadConsumed = approvalService.consumeApproved(
                approvalRecord.getApprovalId(),
                "purchase_order_create",
                "{\"productId\":1,\"quantity\":11}",
                1L,
                "test-session");
        boolean consumed = approvalService.consumeApproved(
                approvalRecord.getApprovalId(),
                "purchase_order_create",
                "{\"quantity\":10,\"productId\":1}",
                1L,
                "test-session");
        boolean secondConsumed = approvalService.consumeApproved(
                approvalRecord.getApprovalId(),
                "purchase_order_create",
                "{\"productId\":1,\"quantity\":10}",
                1L,
                "test-session");

        assertThat(approved).isTrue();
        assertThat(wrongPayloadConsumed).isFalse();
        assertThat(consumed).isTrue();
        assertThat(secondConsumed).isFalse();
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

    private JsonNode json(String value) throws JacksonException {
        return objectMapper.readTree(value);
    }
}

package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateResult;
import com.ecommerce.agent.dto.PurchaseOrderResult;
import com.ecommerce.agent.service.ApprovalService;

@SpringBootTest
class PurchaseOrderToolTest {

    @Autowired
    private PurchaseOrderTool purchaseOrderTool;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

    @Test
    void purchaseOrderQueryReturnsPurchaseOrders() {
        List<PurchaseOrderResult> orders = purchaseOrderTool.purchaseOrderQuery(5);

        assertThat(orders).isNotEmpty();
        assertThat(orders).hasSizeLessThanOrEqualTo(5);
        assertThat(orders.getFirst().poId()).isNotNull();
        assertThat(orders.getFirst().supplierId()).isNotNull();
        assertThat(orders.getFirst().status()).isNotBlank();
        assertThat(orders.getFirst().totalCost()).isNotNull();
    }

    @Test
    void purchaseOrderCreateRequiresApprovalId() {
        PurchaseOrderCreateResult result = purchaseOrderTool.purchaseOrderCreate(
                null,
                1L,
                items(),
                1L,
                "test-session");

        assertThat(result.status()).isEqualTo("approval_required");
        assertThat(result.poId()).isNull();
    }

    @Test
    @Transactional
    void purchaseOrderCreateCreatesPlacedPurchaseOrderAfterApproval() {
        PurchaseOrderCreateRequest request = createRequest(null);
        String approvalId = approvedApprovalId(request);

        PurchaseOrderCreateResult result = purchaseOrderTool.purchaseOrderCreate(
                approvalId,
                request.supplierId(),
                request.items(),
                request.userId(),
                request.sessionId());

        assertThat(result.status()).isEqualTo("created");
        assertThat(result.poId()).isNotNull();
        assertThat(result.poStatus()).isEqualTo("placed");
        assertThat(result.totalCost()).isEqualByComparingTo("125.00");
        assertThat(result.itemCount()).isEqualTo(1);
    }

    private String approvedApprovalId(PurchaseOrderCreateRequest request) {
        ApprovalRequest approvalRequest = approvalPayloadBuilder.purchaseOrderCreateApprovalRequest(request);
        ApprovalRecord approvalRecord = approvalService.createPending(
                approvalRequest.toolName(),
                approvalRequest.operationType(),
                approvalPayloadBuilder.operationPayloadJson(approvalRequest),
                approvalPayloadBuilder.operationDetailJson(approvalRequest),
                approvalRequest.userId(),
                approvalRequest.sessionId());

        assertThat(approvalService.approve(approvalRecord.getApprovalId(), request.userId(), request.sessionId()))
                .isTrue();
        return approvalRecord.getApprovalId();
    }

    private PurchaseOrderCreateRequest createRequest(String approvalId) {
        return new PurchaseOrderCreateRequest(
                approvalId,
                1L,
                items(),
                1L,
                "test-session");
    }

    private List<PurchaseOrderCreateItemRequest> items() {
        return List.of(new PurchaseOrderCreateItemRequest(2L, 10, new BigDecimal("12.50")));
    }
}

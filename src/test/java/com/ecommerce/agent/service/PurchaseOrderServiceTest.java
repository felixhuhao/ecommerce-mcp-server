package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateResult;

@SpringBootTest
class PurchaseOrderServiceTest {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

    @Test
    void findRecentPurchaseOrdersReturnsPurchaseOrders() {
        List<PurchaseOrder> purchaseOrders = purchaseOrderService.findRecentPurchaseOrders(5);

        assertThat(purchaseOrders).isNotEmpty();
        assertThat(purchaseOrders).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void findRecentPurchaseOrdersCapsLargeLimit() {
        List<PurchaseOrder> purchaseOrders = purchaseOrderService.findRecentPurchaseOrders(1000);

        assertThat(purchaseOrders).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void findRecentPurchaseOrdersUsesDefaultLimitWhenLimitInvalid() {
        List<PurchaseOrder> purchaseOrders = purchaseOrderService.findRecentPurchaseOrders(0);

        assertThat(purchaseOrders).isNotEmpty();
        assertThat(purchaseOrders).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void createPurchaseOrderRequiresApprovalId() {
        PurchaseOrderCreateResult result = purchaseOrderService.createPurchaseOrder(createRequest(null));

        assertThat(result.status()).isEqualTo("approval_required");
        assertThat(result.poId()).isNull();
    }

    @Test
    @Transactional
    void createPurchaseOrderCreatesPlacedPurchaseOrderAfterApproval() {
        PurchaseOrderCreateRequest request = createRequest(null);
        String approvalId = approvedApprovalId(request);
        PurchaseOrderCreateRequest approvedRequest = new PurchaseOrderCreateRequest(
                approvalId,
                request.supplierId(),
                request.items(),
                request.userId(),
                request.sessionId());

        PurchaseOrderCreateResult result = purchaseOrderService.createPurchaseOrder(approvedRequest);

        assertThat(result.status()).isEqualTo("created");
        assertThat(result.poId()).isNotNull();
        assertThat(result.supplierId()).isEqualTo(1L);
        assertThat(result.poStatus()).isEqualTo("placed");
        assertThat(result.totalCost()).isEqualByComparingTo("125.00");
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(result.approvalId()).isEqualTo(approvalId);
    }

    @Test
    @Transactional
    void createPurchaseOrderRejectsApprovalBoundToDifferentPayload() {
        PurchaseOrderCreateRequest approvedPayload = createRequest(null);
        String approvalId = approvedApprovalId(approvedPayload);
        PurchaseOrderCreateRequest changedPayload = new PurchaseOrderCreateRequest(
                approvalId,
                approvedPayload.supplierId(),
                List.of(new PurchaseOrderCreateItemRequest(2L, 11, new BigDecimal("12.50"))),
                approvedPayload.userId(),
                approvedPayload.sessionId());

        PurchaseOrderCreateResult result = purchaseOrderService.createPurchaseOrder(changedPayload);

        assertThat(result.status()).isEqualTo("invalid_approval");
        assertThat(result.poId()).isNull();
        assertThat(result.approvalId()).isEqualTo(approvalId);
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
                List.of(new PurchaseOrderCreateItemRequest(2L, 10, new BigDecimal("12.50"))),
                1L,
                "test-session");
    }
}

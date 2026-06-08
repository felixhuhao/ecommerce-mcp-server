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
import com.ecommerce.agent.domain.Inventory;
import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateResult;
import com.ecommerce.agent.dto.PurchaseOrderReceiveRequest;
import com.ecommerce.agent.dto.PurchaseOrderReceiveResult;
import com.ecommerce.agent.mapper.InventoryMapper;
import com.ecommerce.agent.mapper.PurchaseOrderMapper;

@SpringBootTest
class PurchaseOrderServiceTest {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

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

    @Test
    void receivePurchaseOrderRequiresApprovalId() {
        PurchaseOrderReceiveResult result = purchaseOrderService.receivePurchaseOrder(new PurchaseOrderReceiveRequest(
                null,
                1L,
                1L,
                "test-session"));

        assertThat(result.status()).isEqualTo("approval_required");
        assertThat(result.poId()).isNull();
    }

    @Test
    @Transactional
    void receivePurchaseOrderMarksPlacedOrderReceivedAndIncrementsInventory() {
        Long poId = createPlacedPurchaseOrder();
        Inventory inventoryBefore = inventoryMapper.queryInventory(2L, null, 1).getFirst();
        PurchaseOrderReceiveRequest request = receiveRequest(null, poId);
        String approvalId = approvedReceiveApprovalId(request);
        PurchaseOrderReceiveRequest approvedRequest = receiveRequest(approvalId, poId);

        PurchaseOrderReceiveResult result = purchaseOrderService.receivePurchaseOrder(approvedRequest);

        PurchaseOrder purchaseOrder = purchaseOrderMapper.findById(poId);
        Inventory inventoryAfter = inventoryMapper.queryInventory(2L, null, 1).getFirst();
        assertThat(result.status()).isEqualTo("received");
        assertThat(result.poId()).isEqualTo(poId);
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(purchaseOrder.getStatus()).isEqualTo("received");
        assertThat(purchaseOrder.getReceivedAt()).isNotNull();
        assertThat(inventoryAfter.getQuantity()).isEqualTo(inventoryBefore.getQuantity() + 10);
    }

    @Test
    @Transactional
    void receivePurchaseOrderRejectsStaleApprovalWhenOrderNoLongerPlaced() {
        Long poId = createPlacedPurchaseOrder();
        PurchaseOrderReceiveRequest request = receiveRequest(null, poId);
        String approvalId = approvedReceiveApprovalId(request);
        purchaseOrderMapper.markReceivedIfPlaced(poId);

        PurchaseOrderReceiveResult result = purchaseOrderService.receivePurchaseOrder(receiveRequest(approvalId, poId));

        assertThat(result.status()).isEqualTo("not_receivable");
        assertThat(result.poId()).isEqualTo(poId);
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

    private String approvedReceiveApprovalId(PurchaseOrderReceiveRequest request) {
        ApprovalRequest approvalRequest = approvalPayloadBuilder.purchaseOrderReceiveApprovalRequest(request);
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

    private Long createPlacedPurchaseOrder() {
        PurchaseOrderCreateRequest request = createRequest(null);
        String approvalId = approvedApprovalId(request);
        PurchaseOrderCreateResult result = purchaseOrderService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                approvalId,
                request.supplierId(),
                request.items(),
                request.userId(),
                request.sessionId()));
        assertThat(result.status()).isEqualTo("created");
        return result.poId();
    }

    private PurchaseOrderCreateRequest createRequest(String approvalId) {
        return new PurchaseOrderCreateRequest(
                approvalId,
                1L,
                List.of(new PurchaseOrderCreateItemRequest(2L, 10, new BigDecimal("12.50"))),
                1L,
                "test-session");
    }

    private PurchaseOrderReceiveRequest receiveRequest(String approvalId, Long poId) {
        return new PurchaseOrderReceiveRequest(
                approvalId,
                poId,
                1L,
                "test-session");
    }
}

package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.Inventory;
import com.ecommerce.agent.domain.PurchaseOrder;
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
    void createPurchaseOrderFromApprovalRequiresApprovalId() {
        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrderFromApproval(createRequest(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("approvalId must not be blank");
    }

    @Test
    @Transactional
    void createPurchaseOrderFromApprovalCreatesPlacedPurchaseOrder() {
        PurchaseOrderCreateResult result = purchaseOrderService.createPurchaseOrderFromApproval(createRequest("approval-id"));

        assertThat(result.status()).isEqualTo("created");
        assertThat(result.poId()).isNotNull();
        assertThat(result.supplierId()).isEqualTo(1L);
        assertThat(result.poStatus()).isEqualTo("placed");
        assertThat(result.totalCost()).isEqualByComparingTo("125.00");
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(result.approvalId()).isEqualTo("approval-id");
    }

    @Test
    void receivePurchaseOrderFromApprovalRequiresApprovalId() {
        assertThatThrownBy(() -> purchaseOrderService.receivePurchaseOrderFromApproval(new PurchaseOrderReceiveRequest(
                null,
                1L,
                1L,
                "test-session")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("approvalId must not be blank");
    }

    @Test
    @Transactional
    void receivePurchaseOrderFromApprovalMarksPlacedOrderReceivedAndIncrementsInventory() {
        Long poId = createPlacedPurchaseOrder();
        Inventory inventoryBefore = inventoryMapper.queryInventory(2L, null, 1).getFirst();

        PurchaseOrderReceiveResult result = purchaseOrderService.receivePurchaseOrderFromApproval(receiveRequest(
                "approval-id",
                poId));

        PurchaseOrder purchaseOrder = purchaseOrderMapper.findById(poId);
        Inventory inventoryAfter = inventoryMapper.queryInventory(2L, null, 1).getFirst();
        assertThat(result.status()).isEqualTo("received");
        assertThat(result.poId()).isEqualTo(poId);
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(purchaseOrder.getStatus()).isEqualTo("received");
        assertThat(purchaseOrder.getReceivedAt()).isNotNull();
        assertThat(inventoryAfter.getQuantity()).isEqualTo(inventoryBefore.getQuantity() + 10);
    }

    private Long createPlacedPurchaseOrder() {
        PurchaseOrderCreateResult result = purchaseOrderService.createPurchaseOrderFromApproval(createRequest("approval-id"));
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

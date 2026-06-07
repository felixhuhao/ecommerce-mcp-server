package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.PurchaseOrder;

@SpringBootTest
class PurchaseOrderServiceTest {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

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
}

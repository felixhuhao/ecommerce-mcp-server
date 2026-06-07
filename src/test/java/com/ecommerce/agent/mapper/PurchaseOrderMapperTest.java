package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.PurchaseOrder;

@SpringBootTest
class PurchaseOrderMapperTest {

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Test
    void findRecentPurchaseOrdersReturnsPurchaseOrders() {
        List<PurchaseOrder> purchaseOrders = purchaseOrderMapper.findRecentPurchaseOrders(5);

        assertThat(purchaseOrders).isNotEmpty();
        assertThat(purchaseOrders).hasSizeLessThanOrEqualTo(5);
        assertThat(purchaseOrders.getFirst().getPoId()).isNotNull();
        assertThat(purchaseOrders.getFirst().getSupplierId()).isNotNull();
        assertThat(purchaseOrders.getFirst().getStatus()).isNotBlank();
        assertThat(purchaseOrders.getFirst().getTotalCost()).isNotNull();
        assertThat(purchaseOrders.getFirst().getCreatedAt()).isNotNull();
    }
}

package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.PurchaseOrderResult;

@SpringBootTest
class PurchaseOrderToolTest {

    @Autowired
    private PurchaseOrderTool purchaseOrderTool;

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
}

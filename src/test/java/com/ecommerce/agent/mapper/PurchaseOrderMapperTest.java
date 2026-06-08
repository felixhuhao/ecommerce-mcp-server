package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.domain.PurchaseOrderItem;

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

    @Test
    @Transactional
    void insertPurchaseOrderAndItemGeneratesIds() {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setSupplierId(1L);
        purchaseOrder.setStatus("placed");
        purchaseOrder.setTotalCost(new BigDecimal("125.00"));

        int purchaseOrderRows = purchaseOrderMapper.insertPurchaseOrder(purchaseOrder);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPoId(purchaseOrder.getPoId());
        item.setProductId(2L);
        item.setQuantity(10);
        item.setUnitCost(new BigDecimal("12.50"));
        item.setSubtotal(new BigDecimal("125.00"));

        int itemRows = purchaseOrderMapper.insertPurchaseOrderItem(item);

        assertThat(purchaseOrderRows).isEqualTo(1);
        assertThat(purchaseOrder.getPoId()).isNotNull();
        assertThat(itemRows).isEqualTo(1);
        assertThat(item.getPoItemId()).isNotNull();
    }

    @Test
    @Transactional
    void findByIdAndItemsReturnInsertedPurchaseOrder() {
        PurchaseOrder purchaseOrder = insertPurchaseOrderWithOneItem();

        PurchaseOrder found = purchaseOrderMapper.findById(purchaseOrder.getPoId());
        List<PurchaseOrderItem> items = purchaseOrderMapper.findItemsByPoId(purchaseOrder.getPoId());

        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("placed");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getProductId()).isEqualTo(2L);
        assertThat(items.getFirst().getQuantity()).isEqualTo(10);
    }

    @Test
    @Transactional
    void markReceivedIfPlacedOnlyReceivesPlacedPurchaseOrderOnce() {
        PurchaseOrder purchaseOrder = insertPurchaseOrderWithOneItem();

        int firstUpdateRows = purchaseOrderMapper.markReceivedIfPlaced(purchaseOrder.getPoId());
        int secondUpdateRows = purchaseOrderMapper.markReceivedIfPlaced(purchaseOrder.getPoId());
        PurchaseOrder found = purchaseOrderMapper.findById(purchaseOrder.getPoId());

        assertThat(firstUpdateRows).isEqualTo(1);
        assertThat(secondUpdateRows).isZero();
        assertThat(found.getStatus()).isEqualTo("received");
        assertThat(found.getReceivedAt()).isNotNull();
    }

    private PurchaseOrder insertPurchaseOrderWithOneItem() {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setSupplierId(1L);
        purchaseOrder.setStatus("placed");
        purchaseOrder.setTotalCost(new BigDecimal("125.00"));
        purchaseOrderMapper.insertPurchaseOrder(purchaseOrder);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPoId(purchaseOrder.getPoId());
        item.setProductId(2L);
        item.setQuantity(10);
        item.setUnitCost(new BigDecimal("12.50"));
        item.setSubtotal(new BigDecimal("125.00"));
        purchaseOrderMapper.insertPurchaseOrderItem(item);

        return purchaseOrder;
    }
}

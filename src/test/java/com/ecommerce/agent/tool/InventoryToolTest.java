package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.InventoryResult;
import com.ecommerce.agent.dto.InventoryLowStockResult;

@SpringBootTest
class InventoryToolTest {

    @Autowired
    private InventoryTool inventoryTool;

    @Test
    void inventoryLowStockReturnsLowStockResults() {
        List<InventoryLowStockResult> items = inventoryTool.inventoryLowStock(10);

        assertThat(items).isNotEmpty();
        assertThat(items).hasSizeLessThanOrEqualTo(10);
        assertThat(items)
                .allMatch(item -> item.quantity() < item.safetyStock());
        assertThat(items)
                .allMatch(item -> item.shortage().equals(item.safetyStock() - item.quantity()));
        assertThat(items)
                .allMatch(item -> item.sku() != null && !item.sku().isBlank()
                        && item.productName() != null && !item.productName().isBlank());
    }

    @Test
    void inventoryQueryReturnsInventoryResults() {
        List<InventoryResult> items = inventoryTool.inventoryQuery(null, null, 10);

        assertThat(items).isNotEmpty();
        assertThat(items).hasSizeLessThanOrEqualTo(10);
        assertThat(items.getFirst().productId()).isNotNull();
        assertThat(items.getFirst().sku()).isNotBlank();
        assertThat(items.getFirst().productName()).isNotBlank();
        assertThat(items.getFirst().quantity()).isNotNull();
        assertThat(items.getFirst().warehouse()).isNotBlank();
    }

    @Test
    void inventoryQueryFiltersByProductId() {
        List<InventoryResult> items = inventoryTool.inventoryQuery(1L, null, 10);

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(item -> item.productId().equals(1L));
    }

    @Test
    void inventoryQueryReturnsSeededSkuStockLevel() {
        List<InventoryResult> items = inventoryTool.inventoryQuery(9L, null, 10);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("SKU-119");
            assertThat(item.productName()).contains("Power Bank");
            assertThat(item.quantity()).isEqualTo(410);
            assertThat(item.safetyStock()).isEqualTo(51);
        });
    }
}

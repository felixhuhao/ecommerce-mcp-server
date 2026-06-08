package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.Inventory;

@SpringBootTest
class InventoryMapperTest {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Test
    void findLowStockItemsReturnsInventoryBelowSafetyStock() {
        List<Inventory> items = inventoryMapper.findLowStockItems(10);

        assertThat(items).isNotEmpty();
        assertThat(items).hasSizeLessThanOrEqualTo(10);
        assertThat(items)
                .allMatch(item -> item.getQuantity() < item.getSafetyStock());
    }

    @Test
    @Transactional
    void incrementQuantityAddsToInventory() {
        Inventory before = inventoryMapper.queryInventory(2L, null, 1).getFirst();

        int rows = inventoryMapper.incrementQuantity(2L, 7);
        Inventory after = inventoryMapper.queryInventory(2L, null, 1).getFirst();

        assertThat(rows).isEqualTo(1);
        assertThat(after.getQuantity()).isEqualTo(before.getQuantity() + 7);
    }
}

package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
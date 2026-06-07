package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Inventory;

@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Test
    void findLowStockItemsReturnsInventoryBelowSafetyStock() {
        List<Inventory> items = inventoryService.findLowStockItems(10);

        assertThat(items).isNotEmpty();
        assertThat(items).hasSizeLessThanOrEqualTo(10);
        assertThat(items)
                .allMatch(item -> item.getQuantity() < item.getSafetyStock());
    }

    @Test
    void findLowStockItemsUsesDefaultLimitWhenLimitInvalid() {
        List<Inventory> items = inventoryService.findLowStockItems(0);

        assertThat(items).isNotEmpty();
        assertThat(items).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void findLowStockItemsCapsLargeLimit() {
        List<Inventory> items = inventoryService.findLowStockItems(500);

        assertThat(items).hasSizeLessThanOrEqualTo(100);
    }
}
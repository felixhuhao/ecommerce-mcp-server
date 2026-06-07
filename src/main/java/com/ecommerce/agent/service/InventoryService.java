package com.ecommerce.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.domain.Inventory;
import com.ecommerce.agent.mapper.InventoryMapper;

@Service
public class InventoryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final InventoryMapper inventoryMapper;

    public InventoryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    public List<Inventory> findLowStockItems(Integer limit) {
        return inventoryMapper.findLowStockItems(normalizeLimit(limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
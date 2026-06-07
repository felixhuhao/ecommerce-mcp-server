package com.ecommerce.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.mapper.PurchaseOrderMapper;

@Service
public class PurchaseOrderService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final PurchaseOrderMapper purchaseOrderMapper;

    public PurchaseOrderService(PurchaseOrderMapper purchaseOrderMapper) {
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    public List<PurchaseOrder> findRecentPurchaseOrders(Integer limit) {
        return purchaseOrderMapper.findRecentPurchaseOrders(normalizeLimit(limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}

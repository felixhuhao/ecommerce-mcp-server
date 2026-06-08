package com.ecommerce.agent.service;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.dto.StatisticsResult;
import com.ecommerce.agent.mapper.StatsMapper;

@Service
public class StatsService {

    private static final int DEFAULT_TOP_PRODUCT_LIMIT = 5;
    private static final int MAX_TOP_PRODUCT_LIMIT = 20;

    private final StatsMapper statsMapper;

    public StatsService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public StatisticsResult getStatistics(Integer topProductLimit) {
        int normalizedTopProductLimit = normalizeTopProductLimit(topProductLimit);
        return new StatisticsResult(
                statsMapper.inventoryStatistics(),
                statsMapper.orderStatusStatistics(),
                statsMapper.productCategoryStatistics(),
                statsMapper.purchaseOrderStatusStatistics(),
                statsMapper.topProductSalesStatistics(normalizedTopProductLimit));
    }

    private int normalizeTopProductLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TOP_PRODUCT_LIMIT;
        }

        return Math.min(limit, MAX_TOP_PRODUCT_LIMIT);
    }
}

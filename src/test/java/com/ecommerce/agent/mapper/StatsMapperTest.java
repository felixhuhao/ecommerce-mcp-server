package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.InventoryStatistics;
import com.ecommerce.agent.dto.OrderStatusStatistics;
import com.ecommerce.agent.dto.ProductCategoryStatistics;
import com.ecommerce.agent.dto.TopProductSalesStatistics;

@SpringBootTest
class StatsMapperTest {

    @Autowired
    private StatsMapper statsMapper;

    @Test
    void inventoryStatisticsAggregatesInventoryRows() {
        InventoryStatistics statistics = statsMapper.inventoryStatistics();

        assertThat(statistics.productCount()).isPositive();
        assertThat(statistics.lowStockCount()).isBetween(0L, statistics.productCount());
        assertThat(statistics.totalQuantity()).isPositive();
        assertThat(statistics.totalSafetyStock()).isPositive();
    }

    @Test
    void groupedStatisticsReturnSeededBusinessBuckets() {
        List<OrderStatusStatistics> orderStatuses = statsMapper.orderStatusStatistics();
        List<ProductCategoryStatistics> categories = statsMapper.productCategoryStatistics();

        assertThat(orderStatuses).isNotEmpty();
        assertThat(orderStatuses)
                .allMatch(status -> status.status() != null
                        && status.orderCount() > 0
                        && status.totalAmount().compareTo(BigDecimal.ZERO) > 0);
        assertThat(categories).isNotEmpty();
        assertThat(categories)
                .allMatch(category -> category.category() != null
                        && category.productCount() > 0
                        && category.activeProductCount() + category.inactiveProductCount() == category.productCount());
    }

    @Test
    void topProductSalesStatisticsHonorsLimit() {
        List<TopProductSalesStatistics> topProducts = statsMapper.topProductSalesStatistics(3);

        assertThat(topProducts).isNotEmpty();
        assertThat(topProducts).hasSizeLessThanOrEqualTo(3);
        assertThat(topProducts)
                .allMatch(product -> product.productId() != null
                        && product.productName() != null
                        && product.unitsSold() > 0
                        && product.revenue().compareTo(BigDecimal.ZERO) > 0);
    }
}

package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.dto.InventoryStatistics;
import com.ecommerce.agent.dto.OrderStatusStatistics;
import com.ecommerce.agent.dto.ProductCategoryStatistics;
import com.ecommerce.agent.dto.TopProductSalesStatistics;

@SpringBootTest
@Transactional
class StatsMapperTest {

    @Autowired
    private StatsMapper statsMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void topProductSalesStatisticsExcludesUnrealizedOrders() {
        insertProduct(999001L, "Cancelled revenue probe");
        insertOrder(999001L, "cancelled");
        insertOrderItem(999001L, 999001L, 999001L);
        insertOrder(999002L, "pending");
        insertOrderItem(999002L, 999002L, 999001L);

        List<TopProductSalesStatistics> topProducts = statsMapper.topProductSalesStatistics(20);

        assertThat(topProducts)
                .noneMatch(product -> product.productId().equals(999001L));
    }

    private void insertProduct(Long productId, String name) {
        jdbcTemplate.update("""
                INSERT INTO product (
                    product_id,
                    sku,
                    name,
                    category,
                    price,
                    cost,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'electronics', 9999.00, 1000.00, 'active', NOW(), NOW())
                """, productId, "SKU-" + productId, name);
    }

    private void insertOrder(Long orderId, String status) {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    order_id,
                    user_id,
                    total_amount,
                    status,
                    created_at,
                    cancelled_at
                ) VALUES (?, 1, 99999999.00, ?, NOW(), CASE WHEN ? = 'cancelled' THEN NOW() ELSE NULL END)
                """, orderId, status, status);
    }

    private void insertOrderItem(Long itemId, Long orderId, Long productId) {
        jdbcTemplate.update("""
                INSERT INTO order_item (
                    item_id,
                    order_id,
                    product_id,
                    quantity,
                    unit_price,
                    subtotal
                ) VALUES (?, ?, ?, 9999, 9999.00, 99999999.00)
                """, itemId, orderId, productId);
    }
}

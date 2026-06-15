package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.dto.InventoryStatistics;
import com.ecommerce.agent.dto.OrderStatusStatistics;
import com.ecommerce.agent.dto.ProductCategoryStatistics;
import com.ecommerce.agent.dto.PurchaseOrderStatusStatistics;
import com.ecommerce.agent.dto.SalesCategoryStatistics;
import com.ecommerce.agent.dto.TopCustomerSpendStatistics;
import com.ecommerce.agent.dto.TopProductSalesStatistics;

@Mapper
public interface StatsMapper {

    @Select("""
            SELECT
                COUNT(*) AS productCount,
                SUM(CASE WHEN quantity < safety_stock THEN 1 ELSE 0 END) AS lowStockCount,
                CAST(COALESCE(SUM(quantity), 0) AS SIGNED) AS totalQuantity,
                CAST(COALESCE(SUM(safety_stock), 0) AS SIGNED) AS totalSafetyStock
            FROM inventory
            """)
    InventoryStatistics inventoryStatistics();

    @Select("""
            SELECT
                status,
                COUNT(*) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS totalAmount
            FROM orders
            GROUP BY status
            ORDER BY orderCount DESC, status
            """)
    List<OrderStatusStatistics> orderStatusStatistics();

    @Select("""
            SELECT
                category,
                COUNT(*) AS productCount,
                SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END) AS activeProductCount,
                SUM(CASE WHEN status <> 'active' THEN 1 ELSE 0 END) AS inactiveProductCount
            FROM product
            GROUP BY category
            ORDER BY productCount DESC, category
            """)
    List<ProductCategoryStatistics> productCategoryStatistics();

    @Select("""
            SELECT
                p.category AS category,
                CAST(SUM(oi.quantity) AS SIGNED) AS unitsSold,
                COALESCE(SUM(oi.subtotal), 0) AS totalSales
            FROM order_item oi
            JOIN orders o ON o.order_id = oi.order_id
            JOIN product p ON p.product_id = oi.product_id
            WHERE o.status IN ('paid', 'shipped', 'completed')
            GROUP BY p.category
            ORDER BY totalSales DESC, category
            """)
    List<SalesCategoryStatistics> salesCategoryStatistics();

    @Select("""
            SELECT
                status,
                COUNT(*) AS purchaseOrderCount,
                COALESCE(SUM(total_cost), 0) AS totalCost
            FROM purchase_order
            GROUP BY status
            ORDER BY purchaseOrderCount DESC, status
            """)
    List<PurchaseOrderStatusStatistics> purchaseOrderStatusStatistics();

    @Select("""
            SELECT
                p.product_id AS productId,
                p.name AS productName,
                CAST(SUM(oi.quantity) AS SIGNED) AS unitsSold,
                COALESCE(SUM(oi.subtotal), 0) AS revenue
            FROM order_item oi
            JOIN orders o ON o.order_id = oi.order_id
            JOIN product p ON p.product_id = oi.product_id
            WHERE o.status IN ('paid', 'shipped', 'completed')
            GROUP BY p.product_id, p.name
            ORDER BY revenue DESC, unitsSold DESC, p.product_id
            LIMIT #{limit}
            """)
    List<TopProductSalesStatistics> topProductSalesStatistics(@Param("limit") Integer limit);

    @Select("""
            SELECT
                u.user_id AS userId,
                u.username,
                CAST(COUNT(o.order_id) AS SIGNED) AS orderCount,
                COALESCE(SUM(o.total_amount), 0) AS totalSpend
            FROM orders o
            JOIN user u ON u.user_id = o.user_id
            WHERE o.status IN ('paid', 'shipped', 'completed')
            GROUP BY u.user_id, u.username
            ORDER BY totalSpend DESC, orderCount DESC, u.user_id
            LIMIT #{limit}
            """)
    List<TopCustomerSpendStatistics> topCustomerSpendStatistics(@Param("limit") Integer limit);
}

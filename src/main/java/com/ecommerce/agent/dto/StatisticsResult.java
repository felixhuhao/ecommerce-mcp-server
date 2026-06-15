package com.ecommerce.agent.dto;

import java.util.List;

public record StatisticsResult(
        InventoryStatistics inventory,
        List<OrderStatusStatistics> ordersByStatus,
        List<ProductCategoryStatistics> productsByCategory,
        List<SalesCategoryStatistics> salesByCategory,
        List<PurchaseOrderStatusStatistics> purchaseOrdersByStatus,
        List<TopProductSalesStatistics> topProductsByRevenue,
        List<TopCustomerSpendStatistics> topCustomersBySpend) {
}

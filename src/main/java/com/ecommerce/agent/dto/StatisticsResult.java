package com.ecommerce.agent.dto;

import java.util.List;

public record StatisticsResult(
        InventoryStatistics inventory,
        List<OrderStatusStatistics> ordersByStatus,
        List<ProductCategoryStatistics> productsByCategory,
        List<SalesCategoryStatistics> salesByCategory,
        List<SalesDropWowStatistics> salesDropWow,
        List<PurchaseOrderStatusStatistics> purchaseOrdersByStatus,
        List<TopProductSalesStatistics> topProductsByRevenue,
        List<TopCustomerSpendStatistics> topCustomersBySpend) {
}

package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.ProductResult;
import com.ecommerce.agent.service.ProductService;

@Component
public class ProductTool {

    private final ProductService productService;

    public ProductTool(ProductService productService) {
        this.productService = productService;
    }

    @McpTool(
            name = "product_query",
            description = "Read active product catalog rows. Use when the agent needs productId, "
                    + "SKU, name, category, price, cost, or status for product lookup or analysis. "
                    + "Filter with keyword when the operator gives a SKU, product name, or category; "
                    + "omit keyword only for a small catalog sample.")
    public List<ProductResult> productQuery(
            @McpToolParam(required = false, description = "Optional SKU, product name, or category "
                    + "keyword to match active products.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of active product rows "
                    + "to return. Use a small value for lookup; omit for the service default.") Integer limit) {
        return productService.searchActiveProducts(keyword, limit)
                .stream()
                .map(ProductResult::from)
                .toList();
    }

    @McpTool(
            name = "product_search",
            description = "Resolve a product identifier to active product rows. Use first when the "
                    + "operator gives a SKU, product name, or category and another tool needs "
                    + "productId or authoritative product cost. Do not use for inventory quantities "
                    + "or order history.")
    public List<ProductResult> productSearch(
            @McpToolParam(description = "Required SKU, product name, or category keyword to search.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of matching active products to return.") Integer limit) {
        return productService.searchActiveProducts(keyword, limit)
                .stream()
                .map(ProductResult::from)
                .toList();
    }
}

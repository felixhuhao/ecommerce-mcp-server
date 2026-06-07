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

    @McpTool(name = "product_query", description = "Query active products with an optional keyword.")
    public List<ProductResult> productQuery(
            @McpToolParam(required = false, description = "Product name or category keyword.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of products to return.") Integer limit) {
        return productService.searchActiveProducts(keyword, limit)
                .stream()
                .map(ProductResult::from)
                .toList();
    }

    @McpTool(name = "product_search", description = "Search active products by product name or category.")
    public List<ProductResult> productSearch(
            @McpToolParam(description = "Product name or category keyword.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of products to return.") Integer limit) {
        return productService.searchActiveProducts(keyword, limit)
                .stream()
                .map(ProductResult::from)
                .toList();
    }
}

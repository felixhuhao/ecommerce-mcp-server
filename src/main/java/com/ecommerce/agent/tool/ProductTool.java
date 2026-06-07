package com.ecommerce.agent.tool;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.ecommerce.agent.domain.Product;
import com.ecommerce.agent.service.ProductService;

@Component
public class ProductTool {

    private final ProductService productService;

    public ProductTool(ProductService productService) {
        this.productService = productService;
    }

    @McpTool(name = "product_list", description = "List active products with an optional limit.")
    public List<Product> productList(
            @McpToolParam(required = false, description = "Maximum number of products to return.") Integer limit) {
        return productService.findActiveProducts(limit);
    }

    @McpTool(name = "product_search", description = "Search active products by product name or category.")
    public List<Product> productSearch(
            @McpToolParam(description = "Product name or category keyword.") String keyword,
            @McpToolParam(required = false, description = "Maximum number of products to return.") Integer limit) {
        return productService.searchActiveProducts(keyword, limit);
    }
}

package com.ecommerce.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.domain.Product;
import com.ecommerce.agent.mapper.ProductMapper;

@Service
public class ProductService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<Product> findActiveProducts(Integer limit) {
        return productMapper.findActiveProducts(normalizeLimit(limit));
    }

    public List<Product> searchActiveProducts(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return findActiveProducts(limit);
        }

        return productMapper.searchActiveProducts(keyword.trim(), normalizeLimit(limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}

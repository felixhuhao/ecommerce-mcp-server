package com.ecommerce.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.domain.Supplier;
import com.ecommerce.agent.mapper.SupplierMapper;

@Service
public class SupplierService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final SupplierMapper supplierMapper;

    public SupplierService(SupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    public List<Supplier> findTopSuppliers(Integer limit) {
        return supplierMapper.findTopSuppliers(normalizeLimit(limit));
    }

    public List<Supplier> searchSuppliers(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return findTopSuppliers(limit);
        }

        return supplierMapper.searchSuppliers(keyword.trim(), normalizeLimit(limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
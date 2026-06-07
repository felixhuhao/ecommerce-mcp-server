package com.ecommerce.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.domain.Customer;
import com.ecommerce.agent.mapper.CustomerMapper;

@Service
public class CustomerService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final CustomerMapper customerMapper;

    public CustomerService(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    public List<Customer> queryCustomers(Long userId, String keyword, Integer level, Integer limit) {
        if (userId != null) {
            Customer customer = customerMapper.findByUserId(userId);
            return customer == null ? List.of() : List.of(customer);
        }

        if (keyword != null && !keyword.isBlank()) {
            return customerMapper.searchCustomers(keyword.trim(), normalizeLimit(limit));
        }

        if (level != null) {
            return customerMapper.findByLevel(level, normalizeLimit(limit));
        }

        return customerMapper.findRecentCustomers(normalizeLimit(limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}

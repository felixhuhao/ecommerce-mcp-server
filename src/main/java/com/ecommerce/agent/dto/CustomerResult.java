package com.ecommerce.agent.dto;

import java.time.LocalDateTime;

import com.ecommerce.agent.domain.Customer;

public record CustomerResult(
        Long userId,
        String username,
        String phone,
        String email,
        String address,
        Integer level,
        LocalDateTime registeredAt) {

    public static CustomerResult from(Customer customer) {
        return new CustomerResult(
                customer.getUserId(),
                customer.getUsername(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getLevel(),
                customer.getRegisteredAt());
    }
}

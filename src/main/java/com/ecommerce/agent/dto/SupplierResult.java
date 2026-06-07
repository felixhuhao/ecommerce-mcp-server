package com.ecommerce.agent.dto;

import java.math.BigDecimal;

import com.ecommerce.agent.domain.Supplier;

public record SupplierResult(
        Long supplierId,
        String name,
        String contactPerson,
        String phone,
        String email,
        BigDecimal rating,
        Integer leadTime) {

    public static SupplierResult from(Supplier supplier) {
        return new SupplierResult(
                supplier.getSupplierId(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getRating(),
                supplier.getLeadTime());
    }
}
package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Supplier;

@SpringBootTest
class SupplierServiceTest {

    @Autowired
    private SupplierService supplierService;

    @Test
    void findTopSuppliersReturnsSuppliers() {
        List<Supplier> suppliers = supplierService.findTopSuppliers(5);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers).hasSizeLessThanOrEqualTo(5);
        assertThat(suppliers.getFirst().getSupplierId()).isNotNull();
        assertThat(suppliers.getFirst().getName()).isNotBlank();
    }

    @Test
    void searchSuppliersUsesKeyword() {
        List<Supplier> suppliers = supplierService.searchSuppliers("深圳", 10);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers)
                .allMatch(supplier -> supplier.getName().contains("深圳")
                        || supplier.getContactPerson().contains("深圳"));
    }

    @Test
    void searchSuppliersFallsBackToTopSuppliersWhenKeywordBlank() {
        List<Supplier> suppliers = supplierService.searchSuppliers("   ", 5);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void findTopSuppliersCapsLargeLimit() {
        List<Supplier> suppliers = supplierService.findTopSuppliers(500);

        assertThat(suppliers).hasSizeLessThanOrEqualTo(50);
    }
}
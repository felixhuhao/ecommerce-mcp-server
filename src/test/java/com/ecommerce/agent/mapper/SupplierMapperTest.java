package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Supplier;

@SpringBootTest
class SupplierMapperTest {

    @Autowired
    private SupplierMapper supplierMapper;

    @Test
    void findTopSuppliersReturnsSuppliers() {
        List<Supplier> suppliers = supplierMapper.findTopSuppliers(5);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers).hasSizeLessThanOrEqualTo(5);
        assertThat(suppliers.getFirst().getSupplierId()).isNotNull();
        assertThat(suppliers.getFirst().getName()).isNotBlank();
    }

    @Test
    void searchSuppliersReturnsMatchingSuppliers() {
        List<Supplier> suppliers = supplierMapper.searchSuppliers("深圳", 10);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers).hasSizeLessThanOrEqualTo(10);
        assertThat(suppliers)
                .allMatch(supplier -> supplier.getName().contains("深圳")
                        || supplier.getContactPerson().contains("深圳"));
    }
}
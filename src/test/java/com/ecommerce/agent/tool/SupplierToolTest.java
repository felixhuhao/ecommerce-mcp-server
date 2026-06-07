package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.SupplierResult;

@SpringBootTest
class SupplierToolTest {

    @Autowired
    private SupplierTool supplierTool;

    @Test
    void supplierTopReturnsSupplierResults() {
        List<SupplierResult> suppliers = supplierTool.supplierTop(5);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers).hasSizeLessThanOrEqualTo(5);
        assertThat(suppliers.getFirst().supplierId()).isNotNull();
        assertThat(suppliers.getFirst().name()).isNotBlank();
    }

    @Test
    void supplierQueryReturnsMatchingSupplierResults() {
        List<SupplierResult> suppliers = supplierTool.supplierQuery("深圳", 10);

        assertThat(suppliers).isNotEmpty();
        assertThat(suppliers)
                .allMatch(supplier -> supplier.name().contains("深圳")
                        || (supplier.contactPerson() != null
                                && supplier.contactPerson().contains("深圳")));
    }
}

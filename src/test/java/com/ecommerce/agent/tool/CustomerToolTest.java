package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.CustomerResult;

@SpringBootTest
class CustomerToolTest {

    @Autowired
    private CustomerTool customerTool;

    @Test
    void userQueryReturnsCustomerResults() {
        List<CustomerResult> customers = customerTool.userQuery(null, null, null, 5);

        assertThat(customers).isNotEmpty();
        assertThat(customers).hasSizeLessThanOrEqualTo(5);
        assertThat(customers.getFirst().userId()).isNotNull();
        assertThat(customers.getFirst().username()).isNotBlank();
    }

    @Test
    void userQueryFindsCustomerByUserId() {
        List<CustomerResult> customers = customerTool.userQuery(1L, null, null, 10);

        assertThat(customers).hasSize(1);
        assertThat(customers.getFirst().userId()).isEqualTo(1L);
    }

    @Test
    void userQuerySearchesCustomersByKeyword() {
        List<CustomerResult> customers = customerTool.userQuery(null, "user1@example.com", null, 10);

        assertThat(customers).isNotEmpty();
        assertThat(customers)
                .allMatch(customer -> customer.username().contains("user1@example.com")
                        || customer.email().contains("user1@example.com")
                        || customer.phone().contains("user1@example.com"));
    }
}

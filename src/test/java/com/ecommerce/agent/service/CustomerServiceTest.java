package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Customer;

@SpringBootTest
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    @Test
    void queryCustomersFindsByUserId() {
        List<Customer> customers = customerService.queryCustomers(1L, null, null, 10);

        assertThat(customers).hasSize(1);
        assertThat(customers.getFirst().getUserId()).isEqualTo(1L);
    }

    @Test
    void queryCustomersUsesKeyword() {
        List<Customer> customers = customerService.queryCustomers(null, "user1@example.com", null, 10);

        assertThat(customers).isNotEmpty();
        assertThat(customers)
                .allMatch(customer -> customer.getUsername().contains("user1@example.com")
                        || customer.getEmail().contains("user1@example.com")
                        || customer.getPhone().contains("user1@example.com"));
    }

    @Test
    void queryCustomersUsesLevel() {
        List<Customer> customers = customerService.queryCustomers(null, null, 4, 10);

        assertThat(customers).isNotEmpty();
        assertThat(customers).allMatch(customer -> customer.getLevel() == 4);
    }

    @Test
    void queryCustomersFallsBackToRecentCustomersWhenFiltersBlank() {
        List<Customer> customers = customerService.queryCustomers(null, "   ", null, 5);

        assertThat(customers).isNotEmpty();
        assertThat(customers).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void queryCustomersCapsLargeLimit() {
        List<Customer> customers = customerService.queryCustomers(null, null, null, 500);

        assertThat(customers).hasSizeLessThanOrEqualTo(50);
    }
}

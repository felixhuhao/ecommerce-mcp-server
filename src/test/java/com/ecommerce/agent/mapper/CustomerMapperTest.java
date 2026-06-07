package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Customer;

@SpringBootTest
class CustomerMapperTest {

    @Autowired
    private CustomerMapper customerMapper;

    @Test
    void findRecentCustomersReturnsCustomers() {
        List<Customer> customers = customerMapper.findRecentCustomers(5);

        assertThat(customers).isNotEmpty();
        assertThat(customers).hasSizeLessThanOrEqualTo(5);
        assertThat(customers.getFirst().getUserId()).isNotNull();
        assertThat(customers.getFirst().getUsername()).isNotBlank();
        assertThat(customers.getFirst().getRegisteredAt()).isNotNull();
    }

    @Test
    void findByUserIdReturnsCustomer() {
        Customer customer = customerMapper.findByUserId(1L);

        assertThat(customer).isNotNull();
        assertThat(customer.getUserId()).isEqualTo(1L);
        assertThat(customer.getUsername()).isNotBlank();
    }

    @Test
    void searchCustomersReturnsMatchingCustomers() {
        List<Customer> customers = customerMapper.searchCustomers("user1@example.com", 10);

        assertThat(customers).isNotEmpty();
        assertThat(customers)
                .allMatch(customer -> customer.getUsername().contains("user1@example.com")
                        || customer.getEmail().contains("user1@example.com")
                        || customer.getPhone().contains("user1@example.com"));
    }

    @Test
    void findByLevelReturnsMatchingCustomers() {
        List<Customer> customers = customerMapper.findByLevel(4, 10);

        assertThat(customers).isNotEmpty();
        assertThat(customers).hasSizeLessThanOrEqualTo(10);
        assertThat(customers).allMatch(customer -> customer.getLevel() == 4);
    }
}

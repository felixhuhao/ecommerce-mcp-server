package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.service.CustomerOrderService.CustomerOrderWithItems;

@SpringBootTest
class CustomerOrderServiceTest {

    @Autowired
    private CustomerOrderService customerOrderService;

    @Test
    void queryOrdersReturnsOrdersWithItems() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, 5);

        assertThat(orders).isNotEmpty();
        assertThat(orders).hasSizeLessThanOrEqualTo(5);
        assertThat(orders.getFirst().order().getOrderId()).isNotNull();
        assertThat(orders.getFirst().items()).isNotEmpty();
    }

    @Test
    void queryOrdersFiltersByStatus() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, "pending", 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.order().getStatus().equals("pending"));
    }

    @Test
    void queryOrdersFiltersByUserId() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(10L, null, 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.order().getUserId().equals(10L));
    }

    @Test
    void queryOrdersCapsLargeLimit() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, 500);

        assertThat(orders).hasSizeLessThanOrEqualTo(50);
    }
}
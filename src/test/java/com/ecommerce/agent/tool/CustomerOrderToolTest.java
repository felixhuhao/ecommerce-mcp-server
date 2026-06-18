package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.CustomerOrderResult;

@SpringBootTest
class CustomerOrderToolTest {

    @Autowired
    private CustomerOrderTool customerOrderTool;

    @Test
    void orderQueryReturnsOrdersWithItems() {
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(null, null, null, 5);

        assertThat(orders).isNotEmpty();
        assertThat(orders).hasSizeLessThanOrEqualTo(5);
        assertThat(orders.getFirst().orderId()).isNotNull();
        assertThat(orders.getFirst().userId()).isNotNull();
        assertThat(orders.getFirst().totalAmount()).isNotNull();
        assertThat(orders.getFirst().status()).isNotBlank();
        assertThat(orders.getFirst().items()).isNotEmpty();
        assertThat(orders.getFirst().items().getFirst().productId()).isNotNull();
        assertThat(orders.getFirst().items().getFirst().subtotal()).isNotNull();
    }

    @Test
    void orderQueryFiltersByStatus() {
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(null, null, "pending", 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.status().equals("pending"));
    }

    @Test
    void orderQueryFiltersByUserId() {
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(null, 10L, null, 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.userId().equals(10L));
    }

    @Test
    void orderQueryFiltersByOrderId() {
        Long orderId = customerOrderTool.orderQuery(null, null, null, 1)
                .getFirst()
                .orderId();

        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(orderId, null, null, 10);

        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().orderId()).isEqualTo(orderId);
    }
}

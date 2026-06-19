package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.OrderUpdateResult;
import com.ecommerce.agent.mapper.CustomerOrderMapper;
import com.ecommerce.agent.service.CustomerOrderService.CustomerOrderWithItems;

@SpringBootTest
class CustomerOrderServiceTest {

    @Autowired
    private CustomerOrderService customerOrderService;

    @Autowired
    private CustomerOrderMapper customerOrderMapper;

    @Test
    void queryOrdersReturnsOrdersWithItems() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, null, 5);

        assertThat(orders).isNotEmpty();
        assertThat(orders).hasSizeLessThanOrEqualTo(5);
        assertThat(orders.getFirst().order().getOrderId()).isNotNull();
        assertThat(orders.getFirst().items()).isNotEmpty();
    }

    @Test
    void queryOrdersFiltersByStatus() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, "pending", 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.order().getStatus().equals("pending"));
    }

    @Test
    void queryOrdersFiltersByUserId() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, 10L, null, 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.order().getUserId().equals(10L));
    }

    @Test
    void queryOrdersFiltersByOrderId() {
        Long orderId = customerOrderService.queryOrders(null, null, null, 1)
                .getFirst()
                .order()
                .getOrderId();

        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(orderId, null, null, 10);

        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().order().getOrderId()).isEqualTo(orderId);
        assertThat(orders.getFirst().items()).isNotEmpty();
    }

    @Test
    void queryOrdersCapsLargeLimit() {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, null, 500);

        assertThat(orders).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    @Transactional
    void queryOrdersFiltersStalePendingOrdersByCreatedAtOldestFirst() {
        long userId = 99001L;
        CustomerOrder fresh = insertOrder(userId, "pending", LocalDateTime.now().minusHours(2), null);
        CustomerOrder staleOlder = insertOrder(userId, "pending", LocalDateTime.now().minusHours(80), null);
        CustomerOrder staleNewer = insertOrder(userId, "pending", LocalDateTime.now().minusHours(60), null);

        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(
                null,
                userId,
                "pending",
                10,
                48);

        List<Long> orderIds = orders.stream()
                .map(order -> order.order().getOrderId())
                .toList();
        assertThat(orderIds).contains(staleOlder.getOrderId(), staleNewer.getOrderId());
        assertThat(orderIds).doesNotContain(fresh.getOrderId());
        assertThat(orderIds.indexOf(staleOlder.getOrderId())).isLessThan(orderIds.indexOf(staleNewer.getOrderId()));
    }

    @Test
    @Transactional
    void queryOrdersFiltersStalePaidOrdersByPaidAtNotCreatedAt() {
        long userId = 99002L;
        CustomerOrder recentlyPaidOldOrder = insertOrder(
                userId,
                "paid",
                LocalDateTime.now().minusHours(80),
                LocalDateTime.now().minusHours(2));
        CustomerOrder stalePaid = insertOrder(
                userId,
                "paid",
                LocalDateTime.now().minusHours(80),
                LocalDateTime.now().minusHours(30));

        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(
                null,
                userId,
                "paid",
                10,
                24);

        List<Long> orderIds = orders.stream()
                .map(order -> order.order().getOrderId())
                .toList();
        assertThat(orderIds).contains(stalePaid.getOrderId());
        assertThat(orderIds).doesNotContain(recentlyPaidOldOrder.getOrderId());
    }

    @Test
    void updateOrderFromApprovalRequiresApprovalId() {
        assertThatThrownBy(() -> customerOrderService.updateOrderFromApproval(updateRequest(null, 1L, "shipped")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("approvalId must not be blank");
    }

    @Test
    @Transactional
    void updateOrderFromApprovalUpdatesPaidOrderToShipped() {
        Long orderId = firstOrderIdWithStatus("paid");

        OrderUpdateResult result = customerOrderService.updateOrderFromApproval(updateRequest(
                "approval-id",
                orderId,
                "shipped"));

        CustomerOrder order = customerOrderMapper.selectById(orderId);
        assertThat(result.status()).isEqualTo("updated");
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.previousStatus()).isEqualTo("paid");
        assertThat(result.newStatus()).isEqualTo("shipped");
        assertThat(order.getStatus()).isEqualTo("shipped");
        assertThat(order.getShippedAt()).isNotNull();
    }

    private Long firstOrderIdWithStatus(String status) {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, status, 1);
        assertThat(orders).isNotEmpty();
        return orders.getFirst().order().getOrderId();
    }

    private CustomerOrder insertOrder(Long userId, String status, LocalDateTime createdAt, LocalDateTime paidAt) {
        CustomerOrder order = new CustomerOrder();
        order.setUserId(userId);
        order.setTotalAmount(BigDecimal.valueOf(123.45));
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        order.setPaidAt(paidAt);
        customerOrderMapper.insert(order);
        return order;
    }

    private OrderUpdateRequest updateRequest(String approvalId, Long orderId, String newStatus) {
        return new OrderUpdateRequest(
                approvalId,
                orderId,
                newStatus,
                1L,
                "test-session");
    }
}

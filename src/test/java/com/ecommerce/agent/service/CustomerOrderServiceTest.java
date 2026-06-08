package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.dto.ApprovalRequest;
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

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

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

    @Test
    void updateOrderRequiresApprovalId() {
        OrderUpdateResult result = customerOrderService.updateOrder(updateRequest(null, 1L, "shipped"));

        assertThat(result.status()).isEqualTo("approval_required");
        assertThat(result.orderId()).isNull();
    }

    @Test
    @Transactional
    void updateOrderUpdatesPaidOrderToShippedAfterApproval() {
        Long orderId = firstOrderIdWithStatus("paid");
        OrderUpdateRequest request = updateRequest(null, orderId, "shipped");
        String approvalId = approvedOrderUpdateApprovalId(request);

        OrderUpdateResult result = customerOrderService.updateOrder(updateRequest(approvalId, orderId, "shipped"));

        CustomerOrder order = customerOrderMapper.selectById(orderId);
        assertThat(result.status()).isEqualTo("updated");
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.previousStatus()).isEqualTo("paid");
        assertThat(result.newStatus()).isEqualTo("shipped");
        assertThat(order.getStatus()).isEqualTo("shipped");
        assertThat(order.getShippedAt()).isNotNull();
    }

    @Test
    @Transactional
    void updateOrderRejectsStaleApprovalWhenOrderStatusChanged() {
        Long orderId = firstOrderIdWithStatus("paid");
        OrderUpdateRequest request = updateRequest(null, orderId, "shipped");
        String approvalId = approvedOrderUpdateApprovalId(request);
        customerOrderMapper.updateStatusIfCurrent(orderId, "paid", "shipped");

        OrderUpdateResult result = customerOrderService.updateOrder(updateRequest(approvalId, orderId, "shipped"));

        assertThat(result.status()).isEqualTo("not_updatable");
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.approvalId()).isEqualTo(approvalId);
    }

    private String approvedOrderUpdateApprovalId(OrderUpdateRequest request) {
        ApprovalRequest approvalRequest = approvalPayloadBuilder.orderUpdateApprovalRequest(request);
        ApprovalRecord approvalRecord = approvalService.createPending(
                approvalRequest.toolName(),
                approvalRequest.operationType(),
                approvalPayloadBuilder.operationPayloadJson(approvalRequest),
                approvalPayloadBuilder.operationDetailJson(approvalRequest),
                approvalRequest.userId(),
                approvalRequest.sessionId());

        assertThat(approvalService.approve(approvalRecord.getApprovalId(), request.userId(), request.sessionId()))
                .isTrue();
        return approvalRecord.getApprovalId();
    }

    private Long firstOrderIdWithStatus(String status) {
        List<CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, status, 1);
        assertThat(orders).isNotEmpty();
        return orders.getFirst().order().getOrderId();
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

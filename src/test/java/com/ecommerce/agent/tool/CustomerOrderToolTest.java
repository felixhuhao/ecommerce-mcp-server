package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.CustomerOrderResult;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.OrderUpdateResult;
import com.ecommerce.agent.service.ApprovalService;

@SpringBootTest
class CustomerOrderToolTest {

    @Autowired
    private CustomerOrderTool customerOrderTool;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

    @Test
    void orderQueryReturnsOrdersWithItems() {
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(null, null, 5);

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
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(null, "pending", 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.status().equals("pending"));
    }

    @Test
    void orderQueryFiltersByUserId() {
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(10L, null, 10);

        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(order -> order.userId().equals(10L));
    }

    @Test
    void orderUpdateRequiresApprovalId() {
        OrderUpdateResult result = customerOrderTool.orderUpdate(
                null,
                1L,
                "shipped",
                1L,
                "test-session");

        assertThat(result.status()).isEqualTo("approval_required");
        assertThat(result.orderId()).isNull();
    }

    @Test
    @Transactional
    void orderUpdateUpdatesPaidOrderToShippedAfterApproval() {
        Long orderId = firstOrderIdWithStatus("paid");
        OrderUpdateRequest request = updateRequest(null, orderId, "shipped");
        String approvalId = approvedOrderUpdateApprovalId(request);

        OrderUpdateResult result = customerOrderTool.orderUpdate(
                approvalId,
                orderId,
                "shipped",
                request.userId(),
                request.sessionId());

        assertThat(result.status()).isEqualTo("updated");
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.previousStatus()).isEqualTo("paid");
        assertThat(result.newStatus()).isEqualTo("shipped");
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
        List<CustomerOrderResult> orders = customerOrderTool.orderQuery(null, status, 1);
        assertThat(orders).isNotEmpty();
        return orders.getFirst().orderId();
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

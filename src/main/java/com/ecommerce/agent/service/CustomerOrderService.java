package com.ecommerce.agent.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.domain.OrderItem;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.OrderUpdateResult;
import com.ecommerce.agent.mapper.CustomerOrderMapper;
import com.ecommerce.agent.mapper.OrderItemMapper;

@Service
public class CustomerOrderService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final CustomerOrderMapper customerOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ApprovalService approvalService;
    private final ApprovalPayloadBuilder approvalPayloadBuilder;

    public CustomerOrderService(
            CustomerOrderMapper customerOrderMapper,
            OrderItemMapper orderItemMapper,
            ApprovalService approvalService,
            ApprovalPayloadBuilder approvalPayloadBuilder) {
        this.customerOrderMapper = customerOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.approvalService = approvalService;
        this.approvalPayloadBuilder = approvalPayloadBuilder;
    }

    public List<CustomerOrderWithItems> queryOrders(Long userId, String status, Integer limit) {
        List<CustomerOrder> orders = customerOrderMapper.selectList(orderQuery(userId, status, normalizeLimit(limit)));
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(CustomerOrder::getOrderId)
                .toList();

        Map<Long, List<OrderItem>> itemsByOrderId = orderItemMapper.selectList(orderItemQuery(orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        return orders.stream()
                .map(order -> new CustomerOrderWithItems(
                        order,
                        itemsByOrderId.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    @Transactional
    public OrderUpdateResult updateOrder(OrderUpdateRequest request) {
        validateUpdateRequest(request);

        if (request.approvalId() == null || request.approvalId().isBlank()) {
            return OrderUpdateResult.approvalRequired();
        }

        CustomerOrder order = customerOrderMapper.selectById(request.orderId());
        if (order == null) {
            return OrderUpdateResult.notUpdatable(
                    request.approvalId(),
                    request.orderId(),
                    "customer order does not exist: " + request.orderId());
        }

        String newStatus = normalizeStatus(request.newStatus());
        ApprovalRequest approvalRequest = approvalPayloadBuilder.orderUpdateApprovalRequest(new OrderUpdateRequest(
                request.approvalId(),
                request.orderId(),
                newStatus,
                request.userId(),
                request.sessionId()));
        String operationPayload;
        try {
            operationPayload = approvalPayloadBuilder.operationPayloadJson(approvalRequest);
        } catch (IllegalArgumentException e) {
            return OrderUpdateResult.notUpdatable(request.approvalId(), request.orderId(), e.getMessage());
        }

        boolean consumed = approvalService.consumeApproved(
                request.approvalId(),
                ApprovalPayloadBuilder.ORDER_UPDATE_TOOL,
                operationPayload,
                request.userId(),
                request.sessionId());

        if (!consumed) {
            return OrderUpdateResult.invalidApproval(request.approvalId());
        }

        int updatedRows = customerOrderMapper.updateStatusIfCurrent(
                request.orderId(),
                order.getStatus(),
                newStatus);
        if (updatedRows != 1) {
            throw new IllegalStateException("customer order status could not be updated: " + request.orderId());
        }

        return OrderUpdateResult.updated(
                request.orderId(),
                order.getStatus(),
                newStatus,
                request.approvalId());
    }

    private LambdaQueryWrapper<CustomerOrder> orderQuery(Long userId, String status, int limit) {
        LambdaQueryWrapper<CustomerOrder> query = new LambdaQueryWrapper<>();
        query.eq(userId != null, CustomerOrder::getUserId, userId)
                .eq(status != null && !status.isBlank(), CustomerOrder::getStatus, status == null ? null : status.trim())
                .orderByDesc(CustomerOrder::getCreatedAt)
                .orderByDesc(CustomerOrder::getOrderId)
                .last("LIMIT " + limit);
        return query;
    }

    private LambdaQueryWrapper<OrderItem> orderItemQuery(List<Long> orderIds) {
        LambdaQueryWrapper<OrderItem> query = new LambdaQueryWrapper<>();
        query.in(OrderItem::getOrderId, orderIds)
                .orderByAsc(OrderItem::getOrderId)
                .orderByAsc(OrderItem::getItemId);
        return query;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private void validateUpdateRequest(OrderUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.orderId() == null || request.orderId() <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (normalizeStatus(request.newStatus()) == null || normalizeStatus(request.newStatus()).isBlank()) {
            throw new IllegalArgumentException("newStatus must not be blank");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.trim().toLowerCase();
    }

    public record CustomerOrderWithItems(CustomerOrder order, List<OrderItem> items) {
    }
}

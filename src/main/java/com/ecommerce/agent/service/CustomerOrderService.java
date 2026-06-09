package com.ecommerce.agent.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.domain.OrderItem;
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

    public CustomerOrderService(
            CustomerOrderMapper customerOrderMapper,
            OrderItemMapper orderItemMapper) {
        this.customerOrderMapper = customerOrderMapper;
        this.orderItemMapper = orderItemMapper;
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
    public OrderUpdateResult updateOrderFromApproval(OrderUpdateRequest request) {
        validateUpdateRequest(request);
        validateApprovalId(request.approvalId());

        CustomerOrder order = customerOrderMapper.selectById(request.orderId());
        if (order == null) {
            throw new IllegalStateException("customer order does not exist: " + request.orderId());
        }

        String newStatus = normalizeStatus(request.newStatus());
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

    private void validateApprovalId(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId must not be blank");
        }
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

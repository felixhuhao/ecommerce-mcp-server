package com.ecommerce.agent.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.domain.PurchaseOrderItem;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateResult;
import com.ecommerce.agent.dto.PurchaseOrderReceiveRequest;
import com.ecommerce.agent.dto.PurchaseOrderReceiveResult;
import com.ecommerce.agent.mapper.InventoryMapper;
import com.ecommerce.agent.mapper.PurchaseOrderMapper;

@Service
public class PurchaseOrderService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String PLACED_STATUS = "placed";

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final InventoryMapper inventoryMapper;
    private final ApprovalService approvalService;
    private final ApprovalPayloadBuilder approvalPayloadBuilder;

    public PurchaseOrderService(
            PurchaseOrderMapper purchaseOrderMapper,
            InventoryMapper inventoryMapper,
            ApprovalService approvalService,
            ApprovalPayloadBuilder approvalPayloadBuilder) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.inventoryMapper = inventoryMapper;
        this.approvalService = approvalService;
        this.approvalPayloadBuilder = approvalPayloadBuilder;
    }

    public List<PurchaseOrder> findRecentPurchaseOrders(Integer limit) {
        return purchaseOrderMapper.findRecentPurchaseOrders(normalizeLimit(limit));
    }

    @Transactional
    public PurchaseOrderCreateResult createPurchaseOrder(PurchaseOrderCreateRequest request) {
        validateCreateRequest(request);

        if (request.approvalId() == null || request.approvalId().isBlank()) {
            return PurchaseOrderCreateResult.approvalRequired();
        }

        ApprovalRequest approvalRequest = approvalPayloadBuilder.purchaseOrderCreateApprovalRequest(request);
        boolean consumed = approvalService.consumeApproved(
                request.approvalId(),
                ApprovalPayloadBuilder.PURCHASE_ORDER_CREATE_TOOL,
                approvalPayloadBuilder.operationPayloadJson(approvalRequest),
                request.userId(),
                request.sessionId());

        if (!consumed) {
            return PurchaseOrderCreateResult.invalidApproval(request.approvalId());
        }

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setSupplierId(request.supplierId());
        purchaseOrder.setStatus(PLACED_STATUS);
        purchaseOrder.setTotalCost(totalCost(request.items()));
        purchaseOrderMapper.insertPurchaseOrder(purchaseOrder);

        request.items().forEach(item -> purchaseOrderMapper.insertPurchaseOrderItem(toPurchaseOrderItem(
                purchaseOrder.getPoId(),
                item)));

        return PurchaseOrderCreateResult.created(
                purchaseOrder.getPoId(),
                purchaseOrder.getSupplierId(),
                purchaseOrder.getTotalCost(),
                request.items().size(),
                request.approvalId());
    }

    @Transactional
    public PurchaseOrderReceiveResult receivePurchaseOrder(PurchaseOrderReceiveRequest request) {
        validateReceiveRequest(request);

        if (request.approvalId() == null || request.approvalId().isBlank()) {
            return PurchaseOrderReceiveResult.approvalRequired();
        }

        ApprovalRequest approvalRequest = approvalPayloadBuilder.purchaseOrderReceiveApprovalRequest(request);
        String operationPayload;
        List<PurchaseOrderItem> items;
        try {
            operationPayload = approvalPayloadBuilder.operationPayloadJson(approvalRequest);
            items = purchaseOrderMapper.findItemsByPoId(request.poId());
        } catch (IllegalArgumentException e) {
            return PurchaseOrderReceiveResult.notReceivable(request.approvalId(), request.poId(), e.getMessage());
        }

        boolean consumed = approvalService.consumeApproved(
                request.approvalId(),
                ApprovalPayloadBuilder.PURCHASE_ORDER_RECEIVE_TOOL,
                operationPayload,
                request.userId(),
                request.sessionId());

        if (!consumed) {
            return PurchaseOrderReceiveResult.invalidApproval(request.approvalId());
        }

        int purchaseOrderRows = purchaseOrderMapper.markReceivedIfPlaced(request.poId());
        if (purchaseOrderRows != 1) {
            throw new IllegalStateException("purchase order could not be marked received: " + request.poId());
        }

        items.forEach(this::incrementInventory);

        return PurchaseOrderReceiveResult.received(request.poId(), items.size(), request.approvalId());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private void validateCreateRequest(PurchaseOrderCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.supplierId() == null || request.supplierId() <= 0) {
            throw new IllegalArgumentException("supplierId must be positive");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }

        request.items().forEach(this::validateCreateItemRequest);
    }

    private void validateReceiveRequest(PurchaseOrderReceiveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.poId() == null || request.poId() <= 0) {
            throw new IllegalArgumentException("poId must be positive");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    private void validateCreateItemRequest(PurchaseOrderCreateItemRequest item) {
        if (item == null) {
            throw new IllegalArgumentException("items must not contain null values");
        }
        if (item.productId() == null || item.productId() <= 0) {
            throw new IllegalArgumentException("productId must be positive");
        }
        if (item.quantity() == null || item.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (item.unitCost() == null || item.unitCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("unitCost must be positive");
        }
    }

    private BigDecimal totalCost(List<PurchaseOrderCreateItemRequest> items) {
        return items.stream()
                .map(this::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PurchaseOrderItem toPurchaseOrderItem(Long poId, PurchaseOrderCreateItemRequest request) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPoId(poId);
        item.setProductId(request.productId());
        item.setQuantity(request.quantity());
        item.setUnitCost(request.unitCost());
        item.setSubtotal(subtotal(request));
        return item;
    }

    private BigDecimal subtotal(PurchaseOrderCreateItemRequest item) {
        return item.unitCost().multiply(BigDecimal.valueOf(item.quantity()));
    }

    private void incrementInventory(PurchaseOrderItem item) {
        int inventoryRows = inventoryMapper.incrementQuantity(item.getProductId(), item.getQuantity());
        if (inventoryRows != 1) {
            throw new IllegalStateException("inventory row does not exist for product: " + item.getProductId());
        }
    }
}

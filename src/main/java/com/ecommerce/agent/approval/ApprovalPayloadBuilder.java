package com.ecommerce.agent.approval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.domain.PurchaseOrderItem;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderReceiveRequest;
import com.ecommerce.agent.mapper.PurchaseOrderMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApprovalPayloadBuilder {

    public static final String PURCHASE_ORDER_CREATE_TOOL = "purchase_order_create";
    public static final String PURCHASE_ORDER_RECEIVE_TOOL = "purchase_order_receive";
    public static final String CREATE_OPERATION = "create";
    public static final String RECEIVE_OPERATION = "receive";

    private static final Set<String> SUPPORTED_WRITE_TOOLS = Set.of(
            PURCHASE_ORDER_CREATE_TOOL,
            PURCHASE_ORDER_RECEIVE_TOOL);

    private final ObjectMapper objectMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public ApprovalPayloadBuilder(ObjectMapper objectMapper, PurchaseOrderMapper purchaseOrderMapper) {
        this.objectMapper = objectMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    public void validateSupportedRequest(ApprovalRequest request) {
        if (request.toolName() == null || !SUPPORTED_WRITE_TOOLS.contains(request.toolName())) {
            throw new IllegalArgumentException("unsupported approval toolName: " + request.toolName());
        }
        if (PURCHASE_ORDER_CREATE_TOOL.equals(request.toolName())
                && !CREATE_OPERATION.equals(request.operationType())) {
            throw new IllegalArgumentException("purchase_order_create operationType must be create");
        }
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(request.toolName())
                && !RECEIVE_OPERATION.equals(request.operationType())) {
            throw new IllegalArgumentException("purchase_order_receive operationType must be receive");
        }
        if (request.operationParams() == null || request.operationParams().isEmpty()) {
            throw new IllegalArgumentException("operationParams must not be empty");
        }
    }

    public String operationPayloadJson(ApprovalRequest request) {
        return toJson(operationPayload(request));
    }

    public String operationDetailJson(ApprovalRequest request) {
        return toJson(operationDetail(request));
    }

    public ApprovalRequest purchaseOrderCreateApprovalRequest(PurchaseOrderCreateRequest request) {
        return new ApprovalRequest(
                PURCHASE_ORDER_CREATE_TOOL,
                CREATE_OPERATION,
                purchaseOrderCreateParams(request),
                request.userId(),
                request.sessionId());
    }

    public ApprovalRequest purchaseOrderReceiveApprovalRequest(PurchaseOrderReceiveRequest request) {
        return new ApprovalRequest(
                PURCHASE_ORDER_RECEIVE_TOOL,
                RECEIVE_OPERATION,
                purchaseOrderReceiveParams(request.poId()),
                request.userId(),
                request.sessionId());
    }

    private Map<String, Object> operationPayload(ApprovalRequest request) {
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(request.toolName())) {
            return purchaseOrderReceivePayload(request);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", request.operationParams());
        return payload;
    }

    private Map<String, Object> operationDetail(ApprovalRequest request) {
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(request.toolName())) {
            return purchaseOrderReceiveDetail(request);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", request.operationParams());
        return detail;
    }

    private Map<String, Object> purchaseOrderReceivePayload(ApprovalRequest request) {
        Long poId = requireLongParam(request.operationParams(), "poId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", purchaseOrderReceiveParams(poId));
        payload.put("currentState", purchaseOrderCurrentState(poId));
        return payload;
    }

    private Map<String, Object> purchaseOrderReceiveDetail(ApprovalRequest request) {
        Long poId = requireLongParam(request.operationParams(), "poId");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", purchaseOrderReceiveParams(poId));
        detail.put("currentState", purchaseOrderCurrentState(poId));
        detail.put("inventoryImpact", purchaseOrderItems(poId).stream()
                .map(this::inventoryImpact)
                .toList());
        return detail;
    }

    private Map<String, Object> purchaseOrderCreateParams(PurchaseOrderCreateRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("supplierId", request.supplierId());
        params.put("items", request.items().stream()
                .map(this::purchaseOrderCreateItemParams)
                .toList());
        return params;
    }

    private Map<String, Object> purchaseOrderReceiveParams(Long poId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("poId", poId);
        return params;
    }

    private Map<String, Object> purchaseOrderCreateItemParams(PurchaseOrderCreateItemRequest item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", item.productId());
        params.put("quantity", item.quantity());
        params.put("unitCost", item.unitCost());
        return params;
    }

    private Map<String, Object> purchaseOrderCurrentState(Long poId) {
        PurchaseOrder purchaseOrder = requireReceivablePurchaseOrder(poId);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("poId", purchaseOrder.getPoId());
        state.put("supplierId", purchaseOrder.getSupplierId());
        state.put("status", purchaseOrder.getStatus());
        state.put("totalCost", purchaseOrder.getTotalCost());
        state.put("items", purchaseOrderItems(poId).stream()
                .map(this::purchaseOrderItemState)
                .toList());
        return state;
    }

    private Map<String, Object> purchaseOrderItemState(PurchaseOrderItem item) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("poItemId", item.getPoItemId());
        state.put("productId", item.getProductId());
        state.put("quantity", item.getQuantity());
        state.put("unitCost", item.getUnitCost());
        state.put("subtotal", item.getSubtotal());
        return state;
    }

    private Map<String, Object> inventoryImpact(PurchaseOrderItem item) {
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("productId", item.getProductId());
        impact.put("quantityDelta", item.getQuantity());
        return impact;
    }

    private PurchaseOrder requireReceivablePurchaseOrder(Long poId) {
        if (poId == null || poId <= 0) {
            throw new IllegalArgumentException("poId must be positive");
        }

        PurchaseOrder purchaseOrder = purchaseOrderMapper.findById(poId);
        if (purchaseOrder == null) {
            throw new IllegalArgumentException("purchase order does not exist: " + poId);
        }
        if (!"placed".equals(purchaseOrder.getStatus())) {
            throw new IllegalArgumentException("purchase order must be placed to receive: " + poId);
        }

        return purchaseOrder;
    }

    private List<PurchaseOrderItem> purchaseOrderItems(Long poId) {
        List<PurchaseOrderItem> items = purchaseOrderMapper.findItemsByPoId(poId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("purchase order has no items: " + poId);
        }
        return items;
    }

    private Long requireLongParam(Map<String, Object> params, String fieldName) {
        Object value = params.get(fieldName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new IllegalArgumentException(fieldName + " must be positive");
    }

    private String titleFor(String toolName) {
        if (PURCHASE_ORDER_CREATE_TOOL.equals(toolName)) {
            return "Create purchase order";
        }
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(toolName)) {
            return "Receive purchase order";
        }

        return "Approve write operation";
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("operation parameters must be JSON serializable", e);
        }
    }
}

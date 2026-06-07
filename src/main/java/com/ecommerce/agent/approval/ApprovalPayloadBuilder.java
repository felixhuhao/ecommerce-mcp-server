package com.ecommerce.agent.approval;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApprovalPayloadBuilder {

    public static final String PURCHASE_ORDER_CREATE_TOOL = "purchase_order_create";
    public static final String CREATE_OPERATION = "create";

    private static final Set<String> SUPPORTED_WRITE_TOOLS = Set.of(PURCHASE_ORDER_CREATE_TOOL);

    private final ObjectMapper objectMapper;

    public ApprovalPayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateSupportedRequest(ApprovalRequest request) {
        if (request.toolName() == null || !SUPPORTED_WRITE_TOOLS.contains(request.toolName())) {
            throw new IllegalArgumentException("unsupported approval toolName: " + request.toolName());
        }
        if (PURCHASE_ORDER_CREATE_TOOL.equals(request.toolName())
                && !CREATE_OPERATION.equals(request.operationType())) {
            throw new IllegalArgumentException("purchase_order_create operationType must be create");
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

    private Map<String, Object> operationPayload(ApprovalRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", request.operationParams());
        return payload;
    }

    private Map<String, Object> operationDetail(ApprovalRequest request) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", request.operationParams());
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

    private Map<String, Object> purchaseOrderCreateItemParams(PurchaseOrderCreateItemRequest item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", item.productId());
        params.put("quantity", item.quantity());
        params.put("unitCost", item.unitCost());
        return params;
    }

    private String titleFor(String toolName) {
        if (PURCHASE_ORDER_CREATE_TOOL.equals(toolName)) {
            return "Create purchase order";
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

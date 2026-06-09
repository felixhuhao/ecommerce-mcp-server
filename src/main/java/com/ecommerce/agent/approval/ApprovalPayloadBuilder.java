package com.ecommerce.agent.approval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.domain.Inventory;
import com.ecommerce.agent.domain.OrderItem;
import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.domain.PurchaseOrderItem;
import com.ecommerce.agent.domain.Product;
import com.ecommerce.agent.domain.Supplier;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderReceiveRequest;
import com.ecommerce.agent.mapper.CustomerOrderMapper;
import com.ecommerce.agent.mapper.InventoryMapper;
import com.ecommerce.agent.mapper.OrderItemMapper;
import com.ecommerce.agent.mapper.ProductMapper;
import com.ecommerce.agent.mapper.PurchaseOrderMapper;
import com.ecommerce.agent.mapper.SupplierMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApprovalPayloadBuilder {

    public static final String PURCHASE_ORDER_CREATE_TOOL = "purchase_order_create";
    public static final String PURCHASE_ORDER_RECEIVE_TOOL = "purchase_order_receive";
    public static final String ORDER_UPDATE_TOOL = "order_update";
    public static final String CREATE_OPERATION = "create";
    public static final String RECEIVE_OPERATION = "receive";
    public static final String UPDATE_OPERATION = "update";

    private static final Set<String> SUPPORTED_WRITE_TOOLS = Set.of(
            PURCHASE_ORDER_CREATE_TOOL,
            PURCHASE_ORDER_RECEIVE_TOOL,
            ORDER_UPDATE_TOOL);

    private final ObjectMapper objectMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final OrderItemMapper orderItemMapper;

    public ApprovalPayloadBuilder(
            ObjectMapper objectMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            SupplierMapper supplierMapper,
            ProductMapper productMapper,
            InventoryMapper inventoryMapper,
            CustomerOrderMapper customerOrderMapper,
            OrderItemMapper orderItemMapper) {
        this.objectMapper = objectMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.supplierMapper = supplierMapper;
        this.productMapper = productMapper;
        this.inventoryMapper = inventoryMapper;
        this.customerOrderMapper = customerOrderMapper;
        this.orderItemMapper = orderItemMapper;
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
        if (ORDER_UPDATE_TOOL.equals(request.toolName())
                && !UPDATE_OPERATION.equals(request.operationType())) {
            throw new IllegalArgumentException("order_update operationType must be update");
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

    public ApprovalRequest orderUpdateApprovalRequest(OrderUpdateRequest request) {
        return new ApprovalRequest(
                ORDER_UPDATE_TOOL,
                UPDATE_OPERATION,
                orderUpdateParams(request.orderId(), normalizeStatus(request.newStatus())),
                request.userId(),
                request.sessionId());
    }

    private Map<String, Object> operationPayload(ApprovalRequest request) {
        if (PURCHASE_ORDER_CREATE_TOOL.equals(request.toolName())) {
            return purchaseOrderCreatePayload(request);
        }
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(request.toolName())) {
            return purchaseOrderReceivePayload(request);
        }
        if (ORDER_UPDATE_TOOL.equals(request.toolName())) {
            return orderUpdatePayload(request);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", request.operationParams());
        return payload;
    }

    private Map<String, Object> operationDetail(ApprovalRequest request) {
        if (PURCHASE_ORDER_CREATE_TOOL.equals(request.toolName())) {
            return purchaseOrderCreateDetail(request);
        }
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(request.toolName())) {
            return purchaseOrderReceiveDetail(request);
        }
        if (ORDER_UPDATE_TOOL.equals(request.toolName())) {
            return orderUpdateDetail(request);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", request.operationParams());
        return detail;
    }

    private Map<String, Object> purchaseOrderCreatePayload(ApprovalRequest request) {
        PurchaseOrderCreateSnapshot snapshot = purchaseOrderCreateSnapshot(request.operationParams());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", snapshot.operationParams());
        payload.put("currentState", snapshot.payloadState());
        return payload;
    }

    private Map<String, Object> purchaseOrderCreateDetail(ApprovalRequest request) {
        PurchaseOrderCreateSnapshot snapshot = purchaseOrderCreateSnapshot(request.operationParams());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", snapshot.operationParams());
        detail.put("currentState", snapshot.detailState());
        detail.put("financialImpact", Map.of("totalCost", snapshot.totalCost()));
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

    private Map<String, Object> orderUpdatePayload(ApprovalRequest request) {
        Long orderId = requireLongParam(request.operationParams(), "orderId");
        String newStatus = requireTextParam(request.operationParams(), "newStatus");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("operationType", request.operationType());
        payload.put("operationParams", orderUpdateParams(orderId, newStatus));
        payload.put("currentState", orderCurrentState(orderId, newStatus));
        return payload;
    }

    private Map<String, Object> orderUpdateDetail(ApprovalRequest request) {
        Long orderId = requireLongParam(request.operationParams(), "orderId");
        String newStatus = requireTextParam(request.operationParams(), "newStatus");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", titleFor(request.toolName()));
        detail.put("toolName", request.toolName());
        detail.put("operationType", request.operationType());
        detail.put("operationParams", orderUpdateParams(orderId, newStatus));
        detail.put("currentState", orderCurrentState(orderId, newStatus));
        detail.put("change", orderUpdateChange(orderId, newStatus));
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

    private PurchaseOrderCreateSnapshot purchaseOrderCreateSnapshot(Map<String, Object> operationParams) {
        Long supplierId = requireLongParam(operationParams, "supplierId");
        Supplier supplier = requireSupplier(supplierId);
        Set<Long> seenProductIds = new HashSet<>();

        List<PurchaseOrderCreateItemSnapshot> items = requireItemParams(operationParams).stream()
                .map(item -> purchaseOrderCreateItemState(item, seenProductIds))
                .toList();
        BigDecimal totalCost = items.stream()
                .map(PurchaseOrderCreateItemSnapshot::subtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        Map<String, Object> normalizedParams = new LinkedHashMap<>();
        normalizedParams.put("supplierId", supplierId);
        normalizedParams.put("items", items.stream()
                .map(PurchaseOrderCreateItemSnapshot::operationParams)
                .toList());

        Map<String, Object> payloadState = new LinkedHashMap<>();
        payloadState.put("supplier", supplierPayloadState(supplier));
        payloadState.put("items", items.stream()
                .map(PurchaseOrderCreateItemSnapshot::payloadState)
                .toList());
        payloadState.put("totalCost", totalCost);

        Map<String, Object> detailState = new LinkedHashMap<>();
        detailState.put("supplier", supplierDetailState(supplier));
        detailState.put("items", items.stream()
                .map(PurchaseOrderCreateItemSnapshot::detailState)
                .toList());
        detailState.put("totalCost", totalCost);

        return new PurchaseOrderCreateSnapshot(normalizedParams, payloadState, detailState, totalCost);
    }

    private PurchaseOrderCreateItemSnapshot purchaseOrderCreateItemState(
            Map<String, Object> itemParams,
            Set<Long> seenProductIds) {
        Long productId = requireLongParam(itemParams, "productId");
        if (!seenProductIds.add(productId)) {
            throw new IllegalArgumentException("purchase order must not contain duplicate productId: " + productId);
        }

        Integer quantity = requirePositiveIntegerParam(itemParams, "quantity");
        BigDecimal unitCost = requireMoneyParam(itemParams, "unitCost");
        BigDecimal subtotal = unitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2);
        Product product = requireActiveProduct(productId);
        Inventory inventory = requireInventory(productId);

        Map<String, Object> operationParams = new LinkedHashMap<>();
        operationParams.put("productId", productId);
        operationParams.put("quantity", quantity);
        operationParams.put("unitCost", unitCost);

        Map<String, Object> payloadState = new LinkedHashMap<>();
        payloadState.put("productId", productId);
        payloadState.put("quantity", quantity);
        payloadState.put("unitCost", unitCost);
        payloadState.put("subtotal", subtotal);
        payloadState.put("product", productPayloadState(product));

        Map<String, Object> detailState = new LinkedHashMap<>(payloadState);
        detailState.put("product", productDetailState(product));
        detailState.put("inventory", inventoryState(inventory));

        return new PurchaseOrderCreateItemSnapshot(operationParams, payloadState, detailState, subtotal);
    }

    private Map<String, Object> purchaseOrderReceiveParams(Long poId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("poId", poId);
        return params;
    }

    private Map<String, Object> orderUpdateParams(Long orderId, String newStatus) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderId", orderId);
        params.put("newStatus", newStatus);
        return params;
    }

    private Map<String, Object> purchaseOrderCreateItemParams(PurchaseOrderCreateItemRequest item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", item.productId());
        params.put("quantity", item.quantity());
        params.put("unitCost", money(item.unitCost(), "unitCost"));
        return params;
    }

    private Map<String, Object> supplierPayloadState(Supplier supplier) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("supplierId", supplier.getSupplierId());
        return state;
    }

    private Map<String, Object> supplierDetailState(Supplier supplier) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("supplierId", supplier.getSupplierId());
        state.put("name", supplier.getName());
        state.put("rating", supplier.getRating());
        state.put("leadTime", supplier.getLeadTime());
        return state;
    }

    private Map<String, Object> productPayloadState(Product product) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("productId", product.getProductId());
        state.put("status", product.getStatus());
        state.put("cost", money(product.getCost(), "product.cost"));
        return state;
    }

    private Map<String, Object> productDetailState(Product product) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("productId", product.getProductId());
        state.put("name", product.getName());
        state.put("category", product.getCategory());
        state.put("status", product.getStatus());
        state.put("cost", money(product.getCost(), "product.cost"));
        return state;
    }

    private Map<String, Object> inventoryState(Inventory inventory) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("productId", inventory.getProductId());
        state.put("quantity", inventory.getQuantity());
        state.put("safetyStock", inventory.getSafetyStock());
        state.put("warehouse", inventory.getWarehouse());
        return state;
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

    private Map<String, Object> orderCurrentState(Long orderId, String newStatus) {
        CustomerOrder order = requireUpdatableOrder(orderId, newStatus);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("orderId", order.getOrderId());
        state.put("userId", order.getUserId());
        state.put("status", order.getStatus());
        state.put("totalAmount", order.getTotalAmount());
        state.put("items", orderItems(orderId).stream()
                .map(this::orderItemState)
                .toList());
        return state;
    }

    private Map<String, Object> orderItemState(OrderItem item) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("itemId", item.getItemId());
        state.put("productId", item.getProductId());
        state.put("quantity", item.getQuantity());
        state.put("unitPrice", item.getUnitPrice());
        state.put("subtotal", item.getSubtotal());
        return state;
    }

    private Map<String, Object> orderUpdateChange(Long orderId, String newStatus) {
        CustomerOrder order = requireUpdatableOrder(orderId, newStatus);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("fromStatus", order.getStatus());
        change.put("toStatus", newStatus);
        return change;
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

    private Supplier requireSupplier(Long supplierId) {
        Supplier supplier = supplierMapper.findById(supplierId);
        if (supplier == null) {
            throw new IllegalArgumentException("supplier does not exist: " + supplierId);
        }
        return supplier;
    }

    private Product requireActiveProduct(Long productId) {
        Product product = productMapper.findById(productId);
        if (product == null) {
            throw new IllegalArgumentException("product does not exist: " + productId);
        }
        if (!"active".equals(product.getStatus())) {
            throw new IllegalArgumentException("product must be active for purchase order: " + productId);
        }
        return product;
    }

    private Inventory requireInventory(Long productId) {
        Inventory inventory = inventoryMapper.findByProductId(productId);
        if (inventory == null) {
            throw new IllegalArgumentException("inventory row does not exist for product: " + productId);
        }
        return inventory;
    }

    private List<PurchaseOrderItem> purchaseOrderItems(Long poId) {
        List<PurchaseOrderItem> items = purchaseOrderMapper.findItemsByPoId(poId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("purchase order has no items: " + poId);
        }
        return items;
    }

    private CustomerOrder requireUpdatableOrder(Long orderId, String newStatus) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }

        CustomerOrder order = customerOrderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("customer order does not exist: " + orderId);
        }
        if (!isAllowedOrderTransition(order.getStatus(), newStatus)) {
            throw new IllegalArgumentException(
                    "order status transition is not allowed: " + order.getStatus() + " -> " + newStatus);
        }

        return order;
    }

    private List<OrderItem> orderItems(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getItemId));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("customer order has no items: " + orderId);
        }
        return items;
    }

    private boolean isAllowedOrderTransition(String currentStatus, String newStatus) {
        return ("pending".equals(currentStatus) && "cancelled".equals(newStatus))
                || ("paid".equals(currentStatus)
                        && ("shipped".equals(newStatus) || "cancelled".equals(newStatus)))
                || ("shipped".equals(currentStatus) && "completed".equals(newStatus));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requireItemParams(Map<String, Object> params) {
        Object value = params.get("items");
        if (value instanceof List<?> items && !items.isEmpty()) {
            return items.stream()
                    .map(item -> {
                        if (item instanceof Map<?, ?> itemMap) {
                            return (Map<String, Object>) itemMap;
                        }
                        throw new IllegalArgumentException("items must contain object values");
                    })
                    .toList();
        }
        throw new IllegalArgumentException("items must not be empty");
    }

    private Long requireLongParam(Map<String, Object> params, String fieldName) {
        Object value = params.get(fieldName);
        if (value instanceof Number || value instanceof String text && !text.isBlank()) {
            try {
                long longValue = new BigDecimal(value.toString()).longValueExact();
                if (longValue > 0) {
                    return longValue;
                }
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException(fieldName + " must be a whole number", e);
            }
        }
        throw new IllegalArgumentException(fieldName + " must be positive");
    }

    private Integer requirePositiveIntegerParam(Map<String, Object> params, String fieldName) {
        Object value = params.get(fieldName);
        if (value instanceof Number || value instanceof String text && !text.isBlank()) {
            try {
                int intValue = new BigDecimal(value.toString()).intValueExact();
                if (intValue > 0) {
                    return intValue;
                }
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException(fieldName + " must be a whole number", e);
            }
        }
        throw new IllegalArgumentException(fieldName + " must be positive");
    }

    private BigDecimal requireMoneyParam(Map<String, Object> params, String fieldName) {
        Object value = params.get(fieldName);
        if (value instanceof Number || value instanceof String) {
            try {
                return money(new BigDecimal(value.toString()), fieldName);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(fieldName + " must be positive", e);
            }
        }
        throw new IllegalArgumentException(fieldName + " must be positive");
    }

    private BigDecimal money(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(fieldName + " must have at most 2 decimal places", e);
        }
    }

    private String requireTextParam(Map<String, Object> params, String fieldName) {
        Object value = params.get(fieldName);
        if (value instanceof String text && !text.isBlank()) {
            return normalizeStatus(text);
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.trim().toLowerCase();
    }

    private String titleFor(String toolName) {
        if (PURCHASE_ORDER_CREATE_TOOL.equals(toolName)) {
            return "Create purchase order";
        }
        if (PURCHASE_ORDER_RECEIVE_TOOL.equals(toolName)) {
            return "Receive purchase order";
        }
        if (ORDER_UPDATE_TOOL.equals(toolName)) {
            return "Update customer order";
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

    private record PurchaseOrderCreateSnapshot(
            Map<String, Object> operationParams,
            Map<String, Object> payloadState,
            Map<String, Object> detailState,
            BigDecimal totalCost) {
    }

    private record PurchaseOrderCreateItemSnapshot(
            Map<String, Object> operationParams,
            Map<String, Object> payloadState,
            Map<String, Object> detailState,
            BigDecimal subtotal) {
    }
}

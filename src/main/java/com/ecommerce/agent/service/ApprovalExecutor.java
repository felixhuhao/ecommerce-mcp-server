package com.ecommerce.agent.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.auth.TrustedActor;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderReceiveRequest;
import com.ecommerce.agent.mapper.ApprovalRecordMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ApprovalExecutor {

    private static final String APPROVED = "approved";
    private static final String CONSUMED = "consumed";
    private static final String FAILED = "failed";
    private static final String INVALIDATED = "invalidated";
    private static final String PAYLOAD_BINDING_MISMATCH = "payload_binding_mismatch";
    private static final String PAYLOAD_INTEGRITY_FAILURE = "payload_integrity_failure";
    private static final String PAYLOAD_INVALID = "payload_invalid";
    private static final String STALE_PRECONDITION = "stale_precondition";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<PurchaseOrderCreateItemRequest>> CREATE_ITEMS_TYPE = new TypeReference<>() {
    };

    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalService approvalService;
    private final ApprovalPayloadBuilder approvalPayloadBuilder;
    private final PurchaseOrderService purchaseOrderService;
    private final CustomerOrderService customerOrderService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ApprovalExecutor(
            ApprovalRecordMapper approvalRecordMapper,
            ApprovalService approvalService,
            ApprovalPayloadBuilder approvalPayloadBuilder,
            PurchaseOrderService purchaseOrderService,
            CustomerOrderService customerOrderService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.approvalRecordMapper = approvalRecordMapper;
        this.approvalService = approvalService;
        this.approvalPayloadBuilder = approvalPayloadBuilder;
        this.purchaseOrderService = purchaseOrderService;
        this.customerOrderService = customerOrderService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ApprovalExecutionOutcome execute(String approvalId, TrustedActor actor) {
        validateRequest(approvalId, actor);
        approvalRecordMapper.expireOpenById(approvalId);

        ApprovalRecord existing = approvalRecordMapper.findById(approvalId);
        if (existing == null) {
            return notFound(approvalId);
        }
        if (!isSameActor(existing, actor)) {
            return notFound(approvalId);
        }
        if (isTerminal(existing)) {
            return terminal(existing);
        }
        if (!APPROVED.equals(existing.getStatus())) {
            return conflict(approvalId, existing.getStatus(), existing.getExecutionResult(),
                    "approval must be approved before execution");
        }

        try {
            return transactionTemplate.execute(status -> executeApprovedInTransaction(approvalId, actor));
        } catch (ApprovalInvalidationException e) {
            return markInvalidatedAfterRollback(approvalId, actor, e.reasonCode(), e.getMessage());
        } catch (DataAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            return markFailedAfterRollback(approvalId, actor, e);
        }
    }

    private ApprovalExecutionOutcome executeApprovedInTransaction(String approvalId, TrustedActor actor) {
        ApprovalRecord locked = approvalRecordMapper.findByIdForUpdate(approvalId);
        if (locked == null) {
            return notFound(approvalId);
        }
        if (!isSameActor(locked, actor)) {
            return notFound(approvalId);
        }
        if (isExpired(locked)) {
            approvalRecordMapper.expireOpenById(approvalId);
            return conflict(approvalId, "expired", null, "approval is expired");
        }
        if (isTerminal(locked)) {
            return terminal(locked);
        }
        if (!APPROVED.equals(locked.getStatus())) {
            return conflict(approvalId, locked.getStatus(), locked.getExecutionResult(),
                    "approval must be approved before execution");
        }

        StoredOperation operation;
        try {
            operation = validateStoredOperation(locked, actor);
        } catch (ApprovalInvalidationException e) {
            return markInvalidated(locked, e.reasonCode(), e.getMessage());
        }

        Object businessResult = executeBusinessWrite(locked, operation, actor);
        String executionResult = toJson(businessResult);
        int updatedRows = approvalRecordMapper.markConsumed(
                approvalId,
                actor.userId(),
                actor.sessionId(),
                executionResult);
        if (updatedRows != 1) {
            throw new IllegalStateException("approval could not be marked consumed: " + approvalId);
        }
        ApprovalRecord consumed = approvalRecordMapper.findById(approvalId);

        return new ApprovalExecutionOutcome(
                approvalId,
                CONSUMED,
                consumed.getExecutionResult(),
                "approval executed successfully",
                HttpStatus.OK);
    }

    private StoredOperation validateStoredOperation(ApprovalRecord approvalRecord, TrustedActor actor) {
        String canonicalStoredPayload;
        try {
            canonicalStoredPayload = approvalService.canonicalizeJson(approvalRecord.getOperationPayload());
        } catch (IllegalArgumentException e) {
            throw invalidation(PAYLOAD_INVALID, "operation payload must be valid JSON", e);
        }
        if (!sameHash(approvalRecord.getOperationHash(), approvalService.sha256(canonicalStoredPayload))) {
            throw invalidation(PAYLOAD_INTEGRITY_FAILURE, "operation payload hash mismatch");
        }

        StoredOperation operation = parseStoredOperation(approvalRecord);
        if (!approvalRecord.getToolName().equals(operation.toolName())) {
            throw invalidation(PAYLOAD_BINDING_MISMATCH, "operation payload toolName does not match approval record");
        }
        if (!approvalRecord.getOperationType().equals(operation.operationType())) {
            throw invalidation(PAYLOAD_BINDING_MISMATCH, "operation payload operationType does not match approval record");
        }

        ApprovalRequest request = new ApprovalRequest(
                operation.toolName(),
                operation.operationType(),
                operation.operationParams(),
                actor.userId(),
                actor.sessionId());
        try {
            approvalPayloadBuilder.validateSupportedRequest(request);
        } catch (IllegalArgumentException e) {
            throw invalidation(PAYLOAD_INVALID, e.getMessage(), e);
        }

        String livePayload;
        try {
            livePayload = approvalPayloadBuilder.operationPayloadJson(request);
        } catch (IllegalArgumentException e) {
            throw invalidation(STALE_PRECONDITION, e.getMessage(), e);
        }
        if (!sameHash(approvalRecord.getOperationHash(), approvalService.sha256(approvalService.canonicalizeJson(livePayload)))) {
            throw invalidation(STALE_PRECONDITION, "approved operation is stale; request a fresh approval");
        }

        return operation;
    }

    private Object executeBusinessWrite(ApprovalRecord approvalRecord, StoredOperation operation, TrustedActor actor) {
        try {
            return switch (operation.toolName()) {
                case ApprovalPayloadBuilder.PURCHASE_ORDER_CREATE_TOOL ->
                        purchaseOrderService.createPurchaseOrderFromApproval(toPurchaseOrderCreateRequest(
                                approvalRecord,
                                operation,
                                actor));
                case ApprovalPayloadBuilder.PURCHASE_ORDER_RECEIVE_TOOL ->
                        purchaseOrderService.receivePurchaseOrderFromApproval(toPurchaseOrderReceiveRequest(
                                approvalRecord,
                                operation,
                                actor));
                case ApprovalPayloadBuilder.ORDER_UPDATE_TOOL ->
                        customerOrderService.updateOrderFromApproval(toOrderUpdateRequest(
                                approvalRecord,
                                operation,
                                actor));
                default -> throw new IllegalArgumentException("unsupported approval toolName: " + operation.toolName());
            };
        } catch (ApprovalPreconditionDriftException e) {
            throw invalidation(STALE_PRECONDITION, e.getMessage(), e);
        }
    }

    private PurchaseOrderCreateRequest toPurchaseOrderCreateRequest(
            ApprovalRecord approvalRecord,
            StoredOperation operation,
            TrustedActor actor) {
        Long supplierId = requireLong(operation.operationParams().get("supplierId"), "supplierId");
        List<PurchaseOrderCreateItemRequest> items = convert(
                operation.operationParams().get("items"),
                CREATE_ITEMS_TYPE,
                "items");
        return new PurchaseOrderCreateRequest(
                approvalRecord.getApprovalId(),
                supplierId,
                items,
                actor.userId(),
                actor.sessionId());
    }

    private PurchaseOrderReceiveRequest toPurchaseOrderReceiveRequest(
            ApprovalRecord approvalRecord,
            StoredOperation operation,
            TrustedActor actor) {
        return new PurchaseOrderReceiveRequest(
                approvalRecord.getApprovalId(),
                requireLong(operation.operationParams().get("poId"), "poId"),
                actor.userId(),
                actor.sessionId());
    }

    private OrderUpdateRequest toOrderUpdateRequest(
            ApprovalRecord approvalRecord,
            StoredOperation operation,
            TrustedActor actor) {
        return new OrderUpdateRequest(
                approvalRecord.getApprovalId(),
                requireLong(operation.operationParams().get("orderId"), "orderId"),
                requireText(operation.operationParams().get("newStatus"), "newStatus"),
                actor.userId(),
                actor.sessionId());
    }

    private ApprovalExecutionOutcome markInvalidated(ApprovalRecord approvalRecord, String reasonCode, String reason) {
        String executionResult = toJson(Map.of(
                "status", INVALIDATED,
                "approvalId", approvalRecord.getApprovalId(),
                "toolName", approvalRecord.getToolName(),
                "reasonCode", reasonCode,
                "message", reason));
        int updatedRows = approvalRecordMapper.markInvalidated(
                approvalRecord.getApprovalId(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId(),
                executionResult);
        if (updatedRows != 1) {
            ApprovalRecord latest = approvalRecordMapper.findById(approvalRecord.getApprovalId());
            return latest == null ? notFound(approvalRecord.getApprovalId()) : terminal(latest);
        }
        ApprovalRecord invalidated = approvalRecordMapper.findById(approvalRecord.getApprovalId());
        return new ApprovalExecutionOutcome(
                approvalRecord.getApprovalId(),
                INVALIDATED,
                invalidated.getExecutionResult(),
                reason,
                HttpStatus.CONFLICT);
    }

    private ApprovalExecutionOutcome markFailedAfterRollback(
            String approvalId,
            TrustedActor actor,
            RuntimeException exception) {
        String executionResult = toJson(Map.of(
                "status", FAILED,
                "approvalId", approvalId,
                "message", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        int updatedRows = approvalRecordMapper.markFailed(
                approvalId,
                actor.userId(),
                actor.sessionId(),
                executionResult);
        if (updatedRows != 1) {
            ApprovalRecord latest = approvalRecordMapper.findById(approvalId);
            return latest == null ? notFound(approvalId) : terminal(latest);
        }
        ApprovalRecord failed = approvalRecordMapper.findById(approvalId);

        return new ApprovalExecutionOutcome(
                approvalId,
                FAILED,
                failed.getExecutionResult(),
                "approval execution failed",
                HttpStatus.CONFLICT);
    }

    private ApprovalExecutionOutcome markInvalidatedAfterRollback(
            String approvalId,
            TrustedActor actor,
            String reasonCode,
            String reason) {
        ApprovalRecord approvalRecord = approvalRecordMapper.findById(approvalId);
        if (approvalRecord == null) {
            return notFound(approvalId);
        }
        if (!isSameActor(approvalRecord, actor)) {
            return notFound(approvalId);
        }
        if (isTerminal(approvalRecord)) {
            return terminal(approvalRecord);
        }
        if (!APPROVED.equals(approvalRecord.getStatus())) {
            return conflict(approvalId, approvalRecord.getStatus(), approvalRecord.getExecutionResult(),
                    "approval must be approved before execution");
        }
        return markInvalidated(approvalRecord, reasonCode, reason);
    }

    private StoredOperation parseStoredOperation(ApprovalRecord approvalRecord) {
        try {
            JsonNode root = objectMapper.readTree(approvalRecord.getOperationPayload());
            if (root == null || !root.isObject()) {
                throw invalidation(PAYLOAD_INVALID, "operation payload must be a JSON object");
            }
            JsonNode params = root.get("operationParams");
            if (params == null || !params.isObject()) {
                throw invalidation(PAYLOAD_INVALID, "operation payload must contain operationParams");
            }
            return new StoredOperation(
                    requireText(root.get("toolName"), "toolName"),
                    requireText(root.get("operationType"), "operationType"),
                    objectMapper.convertValue(params, MAP_TYPE));
        } catch (JacksonException e) {
            throw invalidation(PAYLOAD_INVALID, "operation payload must be valid JSON", e);
        } catch (IllegalArgumentException e) {
            throw invalidation(PAYLOAD_INVALID, e.getMessage(), e);
        }
    }

    private boolean isTerminal(ApprovalRecord approvalRecord) {
        return switch (approvalRecord.getStatus()) {
            case CONSUMED, FAILED, INVALIDATED, "rejected", "expired" -> true;
            default -> false;
        };
    }

    private ApprovalExecutionOutcome terminal(ApprovalRecord approvalRecord) {
        HttpStatus httpStatus = CONSUMED.equals(approvalRecord.getStatus()) ? HttpStatus.OK : HttpStatus.CONFLICT;
        return new ApprovalExecutionOutcome(
                approvalRecord.getApprovalId(),
                approvalRecord.getStatus(),
                approvalRecord.getExecutionResult(),
                "approval is " + approvalRecord.getStatus(),
                httpStatus);
    }

    private ApprovalExecutionOutcome notFound(String approvalId) {
        return new ApprovalExecutionOutcome(
                approvalId,
                "not_found",
                null,
                "approval not found",
                HttpStatus.NOT_FOUND);
    }

    private ApprovalExecutionOutcome conflict(
            String approvalId,
            String status,
            String executionResult,
            String message) {
        return new ApprovalExecutionOutcome(
                approvalId,
                status,
                executionResult,
                message,
                HttpStatus.CONFLICT);
    }

    private void validateRequest(String approvalId, TrustedActor actor) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId must not be blank");
        }
        if (actor == null || actor.userId() == null || actor.userId() <= 0) {
            throw new IllegalArgumentException("actor userId must be positive");
        }
        if (actor.sessionId() == null || actor.sessionId().isBlank()) {
            throw new IllegalArgumentException("actor sessionId must not be blank");
        }
    }

    private boolean isSameActor(ApprovalRecord approvalRecord, TrustedActor actor) {
        return approvalRecord.getUserId().equals(actor.userId())
                && approvalRecord.getSessionId().equals(actor.sessionId());
    }

    private boolean isExpired(ApprovalRecord approvalRecord) {
        return approvalRecord.getExpiresAt() != null
                && !approvalRecord.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private boolean sameHash(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private Long requireLong(Object value, String fieldName) {
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

    private String requireText(Object value, String fieldName) {
        if (value instanceof JsonNode node) {
            return requireText(node.isTextual() ? node.asText() : null, fieldName);
        }
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    private <T> T convert(Object value, TypeReference<T> type, String fieldName) {
        try {
            return objectMapper.convertValue(value, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " is invalid", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("execution result must be JSON serializable", e);
        }
    }

    private ApprovalInvalidationException invalidation(String reasonCode, String message) {
        return new ApprovalInvalidationException(reasonCode, message);
    }

    private ApprovalInvalidationException invalidation(String reasonCode, String message, RuntimeException cause) {
        return new ApprovalInvalidationException(reasonCode, message, cause);
    }

    private record StoredOperation(
            String toolName,
            String operationType,
            Map<String, Object> operationParams) {
    }

    public record ApprovalExecutionOutcome(
            String approvalId,
            String status,
            String executionResult,
            String message,
            HttpStatus httpStatus) {
    }

    private static final class ApprovalInvalidationException extends RuntimeException {

        private final String reasonCode;

        private ApprovalInvalidationException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        private ApprovalInvalidationException(String reasonCode, String message, RuntimeException cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }
}

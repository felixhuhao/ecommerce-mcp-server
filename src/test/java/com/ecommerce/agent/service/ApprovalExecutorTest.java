package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.ecommerce.agent.approval.ApprovalPayloadBuilder;
import com.ecommerce.agent.auth.TrustedActor;
import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.domain.CustomerOrder;
import com.ecommerce.agent.domain.Inventory;
import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.dto.ApprovalRequest;
import com.ecommerce.agent.dto.OrderUpdateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateItemRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateRequest;
import com.ecommerce.agent.dto.PurchaseOrderCreateResult;
import com.ecommerce.agent.dto.PurchaseOrderReceiveRequest;
import com.ecommerce.agent.mapper.ApprovalRecordMapper;
import com.ecommerce.agent.mapper.CustomerOrderMapper;
import com.ecommerce.agent.mapper.InventoryMapper;
import com.ecommerce.agent.mapper.PurchaseOrderMapper;
import com.ecommerce.agent.service.ApprovalExecutor.ApprovalExecutionOutcome;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Sql(scripts = {"classpath:schema.sql", "classpath:data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ApprovalExecutorTest {

    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "executor-test-session";
    private static final TrustedActor ACTOR = new TrustedActor(USER_ID, SESSION_ID);

    @Autowired
    private ApprovalExecutor approvalExecutor;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalPayloadBuilder approvalPayloadBuilder;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private CustomerOrderService customerOrderService;

    @Autowired
    private CustomerOrderMapper customerOrderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void executePurchaseOrderCreateFromStoredPayloadAndReplaysResult() throws JacksonException {
        long purchaseOrderCount = countPurchaseOrders();
        String approvalId = approvedCreateApprovalId(createRequest(null));

        ApprovalExecutionOutcome first = approvalExecutor.execute(approvalId, ACTOR);
        ApprovalExecutionOutcome replay = approvalExecutor.execute(approvalId, ACTOR);

        JsonNode result = json(first.executionResult());
        assertThat(first.status()).isEqualTo("consumed");
        assertThat(result.get("status").asString()).isEqualTo("created");
        assertThat(result.get("poId").asLong()).isPositive();
        assertThat(replay.status()).isEqualTo("consumed");
        assertThat(replay.executionResult()).isEqualTo(first.executionResult());
        assertThat(countPurchaseOrders()).isEqualTo(purchaseOrderCount + 1);
        assertThat(approvalRecordMapper.findById(approvalId).getExecutionResult())
                .isEqualTo(first.executionResult());
    }

    @Test
    void concurrentExecuteCreatesPurchaseOrderOnceAndReplaysStoredResult() throws Exception {
        long purchaseOrderCount = countPurchaseOrders();
        String approvalId = approvedCreateApprovalId(createRequest(null));
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ApprovalExecutionOutcome> executeTask = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent execute did not start");
            }
            return approvalExecutor.execute(approvalId, ACTOR);
        };

        try {
            Future<ApprovalExecutionOutcome> firstFuture = executorService.submit(executeTask);
            Future<ApprovalExecutionOutcome> secondFuture = executorService.submit(executeTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ApprovalExecutionOutcome first = firstFuture.get(10, TimeUnit.SECONDS);
            ApprovalExecutionOutcome second = secondFuture.get(10, TimeUnit.SECONDS);

            assertThat(first.status()).isEqualTo("consumed");
            assertThat(second.status()).isEqualTo("consumed");
            assertThat(first.executionResult()).isEqualTo(second.executionResult());
            assertThat(countPurchaseOrders()).isEqualTo(purchaseOrderCount + 1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void executePurchaseOrderReceiveFromStoredPayload() throws JacksonException {
        PurchaseOrderCreateResult created = purchaseOrderService.createPurchaseOrderFromApproval(createRequest("seed-approval"));
        Inventory inventoryBefore = inventoryMapper.findByProductId(2L);
        String approvalId = approvedReceiveApprovalId(receiveRequest(null, created.poId()));

        ApprovalExecutionOutcome outcome = approvalExecutor.execute(approvalId, ACTOR);

        JsonNode result = json(outcome.executionResult());
        PurchaseOrder purchaseOrder = purchaseOrderMapper.findById(created.poId());
        Inventory inventoryAfter = inventoryMapper.findByProductId(2L);
        assertThat(outcome.status()).isEqualTo("consumed");
        assertThat(result.get("status").asString()).isEqualTo("received");
        assertThat(purchaseOrder.getStatus()).isEqualTo("received");
        assertThat(inventoryAfter.getQuantity()).isEqualTo(inventoryBefore.getQuantity() + 10);
    }

    @Test
    void executeOrderUpdateFromStoredPayload() throws JacksonException {
        Long orderId = firstOrderIdWithStatus("paid");
        String approvalId = approvedOrderUpdateApprovalId(updateRequest(null, orderId, "shipped"));

        ApprovalExecutionOutcome outcome = approvalExecutor.execute(approvalId, ACTOR);

        JsonNode result = json(outcome.executionResult());
        CustomerOrder order = customerOrderMapper.selectById(orderId);
        assertThat(outcome.status()).isEqualTo("consumed");
        assertThat(result.get("status").asString()).isEqualTo("updated");
        assertThat(order.getStatus()).isEqualTo("shipped");
        assertThat(order.getShippedAt()).isNotNull();
    }

    @Test
    void executeInvalidatesStaleProductCostWithoutWriting() throws JacksonException {
        long purchaseOrderCount = countPurchaseOrders();
        String approvalId = approvedCreateApprovalId(createRequest(null));
        jdbcTemplate.update("UPDATE product SET cost = cost + 0.01 WHERE product_id = ?", 2L);

        ApprovalExecutionOutcome outcome = approvalExecutor.execute(approvalId, ACTOR);

        ApprovalRecord approvalRecord = approvalRecordMapper.findById(approvalId);
        JsonNode executionResult = json(approvalRecord.getExecutionResult());
        assertThat(outcome.status()).isEqualTo("invalidated");
        assertThat(approvalRecord.getStatus()).isEqualTo("invalidated");
        assertThat(executionResult.get("reasonCode").asString()).isEqualTo("stale_precondition");
        assertThat(executionResult.get("message").asString()).contains("stale");
        assertThat(countPurchaseOrders()).isEqualTo(purchaseOrderCount);
    }

    @Test
    void executeInvalidatesLivePreconditionFailureWithStaleReasonCode() throws JacksonException {
        PurchaseOrderCreateResult created = purchaseOrderService.createPurchaseOrderFromApproval(createRequest("seed-approval"));
        String approvalId = approvedReceiveApprovalId(receiveRequest(null, created.poId()));
        assertThat(purchaseOrderMapper.markReceivedIfPlaced(created.poId())).isEqualTo(1);

        ApprovalExecutionOutcome outcome = approvalExecutor.execute(approvalId, ACTOR);

        ApprovalRecord approvalRecord = approvalRecordMapper.findById(approvalId);
        JsonNode executionResult = json(approvalRecord.getExecutionResult());
        assertThat(outcome.status()).isEqualTo("invalidated");
        assertThat(approvalRecord.getStatus()).isEqualTo("invalidated");
        assertThat(executionResult.get("reasonCode").asString()).isEqualTo("stale_precondition");
        assertThat(executionResult.get("message").asString()).contains("must be placed");
    }

    @Test
    void executeInvalidatesTamperedOperationHash() throws JacksonException {
        String approvalId = approvedCreateApprovalId(createRequest(null));
        jdbcTemplate.update("UPDATE approval_record SET operation_hash = ? WHERE approval_id = ?", "f".repeat(64), approvalId);

        // operation_detail is display-only; execution trusts operation_payload and its hash.
        ApprovalExecutionOutcome outcome = approvalExecutor.execute(approvalId, ACTOR);

        JsonNode executionResult = json(approvalRecordMapper.findById(approvalId).getExecutionResult());
        assertThat(outcome.status()).isEqualTo("invalidated");
        assertThat(executionResult.get("reasonCode").asString()).isEqualTo("payload_integrity_failure");
        assertThat(executionResult.get("message").asString()).contains("hash mismatch");
    }

    @Test
    void executeRejectsPendingRejectedExpiredAndWrongSessionApprovals() {
        String pendingApprovalId = pendingCreateApprovalId(createRequest(null));
        String rejectedApprovalId = pendingCreateApprovalId(createRequest(null));
        approvalService.reject(rejectedApprovalId, USER_ID, SESSION_ID, "not needed");
        String expiredApprovalId = approvedCreateApprovalId(createRequest(null));
        jdbcTemplate.update("UPDATE approval_record SET expires_at = DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE approval_id = ?",
                expiredApprovalId);
        String wrongSessionApprovalId = approvedCreateApprovalId(createRequest(null));

        ApprovalExecutionOutcome pending = approvalExecutor.execute(pendingApprovalId, ACTOR);
        ApprovalExecutionOutcome rejected = approvalExecutor.execute(rejectedApprovalId, ACTOR);
        ApprovalExecutionOutcome expired = approvalExecutor.execute(expiredApprovalId, ACTOR);
        ApprovalExecutionOutcome wrongSession = approvalExecutor.execute(
                wrongSessionApprovalId,
                new TrustedActor(USER_ID, "other-session"));

        assertThat(pending.status()).isEqualTo("pending");
        assertThat(rejected.status()).isEqualTo("rejected");
        assertThat(expired.status()).isEqualTo("expired");
        assertThat(approvalRecordMapper.findById(expiredApprovalId).getStatus()).isEqualTo("expired");
        assertThat(wrongSession.status()).isEqualTo("not_found");
        assertThat(wrongSession.message()).contains("not found");
    }

    @Test
    void executeMarksFailedWhenBusinessWriteErrorsAfterValidationAndRollsBackPartialEffect() {
        PurchaseOrderCreateResult created = purchaseOrderService.createPurchaseOrderFromApproval(createRequest("seed-approval"));
        String approvalId = approvedReceiveApprovalId(receiveRequest(null, created.poId()));
        jdbcTemplate.update("DELETE FROM inventory WHERE product_id = ?", 2L);

        ApprovalExecutionOutcome outcome = approvalExecutor.execute(approvalId, ACTOR);

        ApprovalRecord approvalRecord = approvalRecordMapper.findById(approvalId);
        PurchaseOrder purchaseOrder = purchaseOrderMapper.findById(created.poId());
        assertThat(outcome.status()).isEqualTo("failed");
        assertThat(approvalRecord.getStatus()).isEqualTo("failed");
        assertThat(approvalRecord.getExecutionResult()).contains("inventory row does not exist");
        assertThat(purchaseOrder.getStatus()).isEqualTo("placed");
    }

    @Test
    void executeLeavesApprovalApprovedWhenBusinessWriteHitsDataAccessException() {
        long purchaseOrderCount = countPurchaseOrders();
        String approvalId = approvedCreateApprovalId(createRequest(null));
        jdbcTemplate.execute("DROP TABLE purchase_order_item");

        assertThatThrownBy(() -> approvalExecutor.execute(approvalId, ACTOR))
                .isInstanceOf(DataAccessException.class);

        ApprovalRecord approvalRecord = approvalRecordMapper.findById(approvalId);
        assertThat(approvalRecord.getStatus()).isEqualTo("approved");
        assertThat(approvalRecord.getExecutionResult()).isNull();
        assertThat(countPurchaseOrders()).isEqualTo(purchaseOrderCount);
    }

    private String pendingCreateApprovalId(PurchaseOrderCreateRequest request) {
        ApprovalRequest approvalRequest = approvalPayloadBuilder.purchaseOrderCreateApprovalRequest(request);
        ApprovalRecord approvalRecord = approvalService.createPending(
                approvalRequest.toolName(),
                approvalRequest.operationType(),
                approvalPayloadBuilder.operationPayloadJson(approvalRequest),
                approvalPayloadBuilder.operationDetailJson(approvalRequest),
                approvalRequest.userId(),
                approvalRequest.sessionId());
        return approvalRecord.getApprovalId();
    }

    private String approvedCreateApprovalId(PurchaseOrderCreateRequest request) {
        String approvalId = pendingCreateApprovalId(request);
        assertThat(approvalService.approve(approvalId, USER_ID, SESSION_ID)).isTrue();
        return approvalId;
    }

    private String approvedReceiveApprovalId(PurchaseOrderReceiveRequest request) {
        ApprovalRequest approvalRequest = approvalPayloadBuilder.purchaseOrderReceiveApprovalRequest(request);
        ApprovalRecord approvalRecord = approvalService.createPending(
                approvalRequest.toolName(),
                approvalRequest.operationType(),
                approvalPayloadBuilder.operationPayloadJson(approvalRequest),
                approvalPayloadBuilder.operationDetailJson(approvalRequest),
                approvalRequest.userId(),
                approvalRequest.sessionId());
        assertThat(approvalService.approve(approvalRecord.getApprovalId(), USER_ID, SESSION_ID)).isTrue();
        return approvalRecord.getApprovalId();
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
        assertThat(approvalService.approve(approvalRecord.getApprovalId(), USER_ID, SESSION_ID)).isTrue();
        return approvalRecord.getApprovalId();
    }

    private PurchaseOrderCreateRequest createRequest(String approvalId) {
        return new PurchaseOrderCreateRequest(
                approvalId,
                1L,
                List.of(new PurchaseOrderCreateItemRequest(2L, 10, new BigDecimal("120.00"))),
                USER_ID,
                SESSION_ID);
    }

    private PurchaseOrderReceiveRequest receiveRequest(String approvalId, Long poId) {
        return new PurchaseOrderReceiveRequest(
                approvalId,
                poId,
                USER_ID,
                SESSION_ID);
    }

    private OrderUpdateRequest updateRequest(String approvalId, Long orderId, String newStatus) {
        return new OrderUpdateRequest(
                approvalId,
                orderId,
                newStatus,
                USER_ID,
                SESSION_ID);
    }

    private Long firstOrderIdWithStatus(String status) {
        List<CustomerOrderService.CustomerOrderWithItems> orders = customerOrderService.queryOrders(null, null, status, 1);
        assertThat(orders).isNotEmpty();
        return orders.getFirst().order().getOrderId();
    }

    private long countPurchaseOrders() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_order", Long.class);
    }

    private JsonNode json(String value) throws JacksonException {
        return objectMapper.readTree(value);
    }
}

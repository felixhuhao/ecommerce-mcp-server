package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.domain.ApprovalRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class ApprovalRecordMapperTest {

    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void insertAndFindByIdReturnsApprovalRecord() throws JacksonException {
        ApprovalRecord approvalRecord = newApprovalRecord();

        approvalRecordMapper.insert(approvalRecord);

        ApprovalRecord found = approvalRecordMapper.findById(approvalRecord.getApprovalId());

        assertThat(found).isNotNull();
        assertThat(found.getApprovalId()).isEqualTo(approvalRecord.getApprovalId());
        assertThat(found.getOperationHash()).isEqualTo(approvalRecord.getOperationHash());
        assertThat(found.getToolName()).isEqualTo("purchase_order_create");
        assertThat(json(found.getOperationPayload()))
                .isEqualTo(json("{\"productId\":1,\"quantity\":10}"));
        assertThat(found.getRejectionReason()).isNull();
        assertThat(found.getRejectedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void approvePendingRequiresMatchingActorAndSession() {
        ApprovalRecord approvalRecord = newApprovalRecord();
        approvalRecordMapper.insert(approvalRecord);

        int wrongSessionRows = approvalRecordMapper.approvePending(
                approvalRecord.getApprovalId(),
                approvalRecord.getUserId(),
                "other-session");
        int approvedRows = approvalRecordMapper.approvePending(
                approvalRecord.getApprovalId(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId());

        assertThat(wrongSessionRows).isZero();
        assertThat(approvedRows).isEqualTo(1);
        assertThat(approvalRecordMapper.findById(approvalRecord.getApprovalId()).getStatus())
                .isEqualTo("approved");
    }

    @Test
    void consumeApprovedIsOneTimeUse() {
        ApprovalRecord approvalRecord = newApprovalRecord();
        approvalRecordMapper.insert(approvalRecord);
        approvalRecordMapper.approvePending(
                approvalRecord.getApprovalId(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId());

        int consumedRows = approvalRecordMapper.consumeApproved(
                approvalRecord.getApprovalId(),
                approvalRecord.getOperationHash(),
                approvalRecord.getToolName(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId());
        int secondConsumedRows = approvalRecordMapper.consumeApproved(
                approvalRecord.getApprovalId(),
                approvalRecord.getOperationHash(),
                approvalRecord.getToolName(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId());

        ApprovalRecord found = approvalRecordMapper.findById(approvalRecord.getApprovalId());
        assertThat(consumedRows).isEqualTo(1);
        assertThat(secondConsumedRows).isZero();
        assertThat(found.getStatus()).isEqualTo("consumed");
        assertThat(found.getConsumedAt()).isNotNull();
    }

    @Test
    void rejectPendingStoresReasonAndRejectTime() {
        ApprovalRecord approvalRecord = newApprovalRecord();
        approvalRecordMapper.insert(approvalRecord);

        int rejectedRows = approvalRecordMapper.rejectPending(
                approvalRecord.getApprovalId(),
                approvalRecord.getUserId(),
                approvalRecord.getSessionId(),
                "Duplicate request");

        ApprovalRecord found = approvalRecordMapper.findById(approvalRecord.getApprovalId());
        assertThat(rejectedRows).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo("rejected");
        assertThat(found.getRejectionReason()).isEqualTo("Duplicate request");
        assertThat(found.getRejectedAt()).isNotNull();
    }

    @Test
    void expireOpenByIdMarksPendingAndApprovedExpiredApprovals() {
        ApprovalRecord pendingApproval = newApprovalRecord();
        pendingApproval.setExpiresAt(EXPIRED_AT);
        approvalRecordMapper.insert(pendingApproval);

        ApprovalRecord approvedApproval = newApprovalRecord();
        approvedApproval.setStatus("approved");
        approvedApproval.setExpiresAt(EXPIRED_AT);
        approvalRecordMapper.insert(approvedApproval);

        ApprovalRecord consumedApproval = newApprovalRecord();
        consumedApproval.setStatus("consumed");
        consumedApproval.setExpiresAt(EXPIRED_AT);
        consumedApproval.setConsumedAt(LocalDateTime.now());
        approvalRecordMapper.insert(consumedApproval);

        int expiredPendingRows = approvalRecordMapper.expireOpenById(pendingApproval.getApprovalId());
        int expiredApprovedRows = approvalRecordMapper.expireOpenById(approvedApproval.getApprovalId());
        int expiredConsumedRows = approvalRecordMapper.expireOpenById(consumedApproval.getApprovalId());

        assertThat(expiredPendingRows).isEqualTo(1);
        assertThat(expiredApprovedRows).isEqualTo(1);
        assertThat(expiredConsumedRows).isZero();
        assertThat(approvalRecordMapper.findById(pendingApproval.getApprovalId()).getStatus())
                .isEqualTo("expired");
        assertThat(approvalRecordMapper.findById(approvedApproval.getApprovalId()).getStatus())
                .isEqualTo("expired");
        assertThat(approvalRecordMapper.findById(consumedApproval.getApprovalId()).getStatus())
                .isEqualTo("consumed");
    }

    private ApprovalRecord newApprovalRecord() {
        ApprovalRecord approvalRecord = new ApprovalRecord();
        approvalRecord.setApprovalId(UUID.randomUUID().toString());
        approvalRecord.setOperationHash("f".repeat(64));
        approvalRecord.setToolName("purchase_order_create");
        approvalRecord.setOperationType("create");
        approvalRecord.setOperationPayload("{\"productId\":1,\"quantity\":10}");
        approvalRecord.setOperationDetail("{\"title\":\"Create purchase order\"}");
        approvalRecord.setUserId(1L);
        approvalRecord.setSessionId("test-session");
        approvalRecord.setStatus("pending");
        approvalRecord.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return approvalRecord;
    }

    private JsonNode json(String value) throws JacksonException {
        return objectMapper.readTree(value);
    }
}

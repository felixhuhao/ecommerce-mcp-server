package com.ecommerce.agent.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.mapper.ApprovalRecordMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ApprovalService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final String PENDING = "pending";

    private final ApprovalRecordMapper approvalRecordMapper;
    private final ObjectMapper objectMapper;

    public ApprovalService(ApprovalRecordMapper approvalRecordMapper, ObjectMapper objectMapper) {
        this.approvalRecordMapper = approvalRecordMapper;
        this.objectMapper = objectMapper;
    }

    public ApprovalRecord createPending(
            String toolName,
            String operationType,
            String operationPayload,
            String operationDetail,
            Long userId,
            String sessionId) {
        return createPending(
                toolName,
                operationType,
                operationPayload,
                operationDetail,
                userId,
                sessionId,
                DEFAULT_TTL);
    }

    ApprovalRecord createPending(
            String toolName,
            String operationType,
            String operationPayload,
            String operationDetail,
            Long userId,
            String sessionId,
            Duration ttl) {
        validateCreateRequest(toolName, operationType, operationPayload, operationDetail, userId, sessionId, ttl);

        String canonicalPayload = canonicalizeJson(operationPayload);
        String canonicalDetail = canonicalizeJson(operationDetail);

        ApprovalRecord approvalRecord = new ApprovalRecord();
        approvalRecord.setApprovalId(UUID.randomUUID().toString());
        approvalRecord.setOperationHash(sha256(canonicalPayload));
        approvalRecord.setToolName(toolName);
        approvalRecord.setOperationType(operationType);
        approvalRecord.setOperationPayload(canonicalPayload);
        approvalRecord.setOperationDetail(canonicalDetail);
        approvalRecord.setUserId(userId);
        approvalRecord.setSessionId(sessionId);
        approvalRecord.setStatus(PENDING);
        approvalRecord.setExpiresAt(LocalDateTime.now().plus(ttl));

        approvalRecordMapper.insert(approvalRecord);
        return approvalRecordMapper.findById(approvalRecord.getApprovalId());
    }

    public Optional<ApprovalRecord> findById(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(approvalRecordMapper.findById(approvalId));
    }

    public boolean approve(String approvalId, Long userId, String sessionId) {
        validateTransitionRequest(approvalId, userId, sessionId);
        return approvalRecordMapper.approvePending(approvalId, userId, sessionId) == 1;
    }

    public boolean reject(String approvalId, Long userId, String sessionId) {
        validateTransitionRequest(approvalId, userId, sessionId);
        return approvalRecordMapper.rejectPending(approvalId, userId, sessionId) == 1;
    }

    public boolean consumeApproved(
            String approvalId,
            String toolName,
            String operationPayload,
            Long userId,
            String sessionId) {
        validateTransitionRequest(approvalId, userId, sessionId);
        requireText(toolName, "toolName");
        requireText(operationPayload, "operationPayload");

        String operationHash = sha256(canonicalizeJson(operationPayload));
        return approvalRecordMapper.consumeApproved(approvalId, operationHash, toolName, userId, sessionId) == 1;
    }

    private void validateCreateRequest(
            String toolName,
            String operationType,
            String operationPayload,
            String operationDetail,
            Long userId,
            String sessionId,
            Duration ttl) {
        requireText(toolName, "toolName");
        requireText(operationType, "operationType");
        requireText(operationPayload, "operationPayload");
        requireText(operationDetail, "operationDetail");
        validateActor(userId, sessionId);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private void validateTransitionRequest(String approvalId, Long userId, String sessionId) {
        requireText(approvalId, "approvalId");
        validateActor(userId, sessionId);
    }

    private void validateActor(Long userId, String sessionId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        requireText(sessionId, "sessionId");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private String canonicalizeJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return objectMapper.writeValueAsString(sortJson(root));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON must be valid", e);
        }
    }

    private JsonNode sortJson(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sortedObject = objectMapper.createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            fields.addAll(node.properties());
            fields.stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(field -> sortedObject.set(field.getKey(), sortJson(field.getValue())));
            return sortedObject;
        }

        if (node.isArray()) {
            ArrayNode sortedArray = objectMapper.createArrayNode();
            node.forEach(item -> sortedArray.add(sortJson(item)));
            return sortedArray;
        }

        return node;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

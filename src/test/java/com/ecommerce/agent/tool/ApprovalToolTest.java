package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.agent.dto.ApprovalResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class ApprovalToolTest {

    @Autowired
    private ApprovalTool approvalTool;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void requestApprovalCreatesPendingApprovalFromStructuredParams() throws JacksonException {
        Map<String, Object> operationParams = Map.of(
                "supplierId", 1,
                "items", List.of(Map.of(
                        "productId", 2,
                        "quantity", 50,
                        "unitCost", new BigDecimal("12.50"))));

        ApprovalResponse response = approvalTool.requestApproval(
                "purchase_order_create",
                "create",
                operationParams,
                1L,
                "test-session");

        assertThat(response.approvalId()).isNotBlank();
        assertThat(response.operationHash()).hasSize(64);
        assertThat(response.toolName()).isEqualTo("purchase_order_create");
        assertThat(response.operationType()).isEqualTo("create");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.sessionId()).isEqualTo("test-session");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(json(response.operationPayload()).get("operationParams").get("supplierId").asInt())
                .isEqualTo(1);
        assertThat(json(response.operationDetail()).get("title").asString())
                .isEqualTo("Create purchase order");
    }

    @Test
    void requestApprovalRejectsUnsupportedToolName() {
        assertThatThrownBy(() -> approvalTool.requestApproval(
                "order_delete",
                "delete",
                Map.of("orderId", 1),
                1L,
                "test-session"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported approval toolName: order_delete");
    }

    private JsonNode json(String value) throws JacksonException {
        return objectMapper.readTree(value);
    }
}

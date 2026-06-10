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

import com.ecommerce.agent.auth.TrustedActorContext;
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

    @Autowired
    private TrustedActorContext trustedActorContext;

    @Test
    void requestApprovalCreatesPendingApprovalFromStructuredParams() throws JacksonException {
        Map<String, Object> operationParams = Map.of(
                "supplierId", 1,
                "items", List.of(Map.of(
                        "productId", 2,
                        "quantity", 50,
                        "unitCost", new BigDecimal("120.00"))));

        ApprovalResponse response = trustedActorContext.runAs(1L, "test-session", () -> approvalTool.requestApproval(
                "purchase_order_create",
                "create",
                operationParams));

        assertThat(response.approvalId()).isNotBlank();
        assertThat(response.operationHash()).hasSize(64);
        assertThat(response.toolName()).isEqualTo("purchase_order_create");
        assertThat(response.operationType()).isEqualTo("create");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.sessionId()).isEqualTo("test-session");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(json(response.operationPayload()).get("operationParams").get("supplierId").asInt())
                .isEqualTo(1);
        assertThat(json(response.operationPayload()).get("operationParams").get("items").get(0).get("unitCost").decimalValue())
                .isEqualByComparingTo("120.00");
        assertThat(json(response.operationPayload()).get("currentState").get("supplier").get("supplierId").asInt())
                .isEqualTo(1);
        assertThat(json(response.operationPayload()).get("currentState").get("items").get(0).get("product").get("status").asString())
                .isEqualTo("active");
        assertThat(json(response.operationPayload()).get("currentState").get("items").get(0).has("inventory"))
                .isFalse();
        assertThat(json(response.operationPayload()).get("currentState").get("totalCost").decimalValue())
                .isEqualByComparingTo("6000.00");
        assertThat(json(response.operationDetail()).get("title").asString())
                .isEqualTo("Create purchase order");
        assertThat(json(response.operationDetail()).get("currentState").get("items").get(0).get("inventory").get("quantity").isNumber())
                .isTrue();
    }

    @Test
    void requestApprovalCanonicalizesMissingUnitCostFromProductCost() throws JacksonException {
        Map<String, Object> operationParams = Map.of(
                "supplierId", 1,
                "items", List.of(Map.of(
                        "productId", 2,
                        "quantity", 10)));

        ApprovalResponse response = trustedActorContext.runAs(1L, "test-session", () -> approvalTool.requestApproval(
                "purchase_order_create",
                "create",
                operationParams));

        JsonNode operationPayload = json(response.operationPayload());
        assertThat(operationPayload.get("operationParams").get("items").get(0).get("unitCost").decimalValue())
                .isEqualByComparingTo("120.00");
        assertThat(operationPayload.get("currentState").get("totalCost").decimalValue())
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void requestApprovalNormalizesSuppliedUnitCostFromProductCost() throws JacksonException {
        Map<String, Object> operationParams = Map.of(
                "supplierId", 1,
                "items", List.of(Map.of(
                        "productId", 2,
                        "quantity", 10,
                        "unitCost", new BigDecimal("12.50"))));

        ApprovalResponse response = trustedActorContext.runAs(1L, "test-session", () -> approvalTool.requestApproval(
                "purchase_order_create",
                "create",
                operationParams));

        JsonNode operationPayload = json(response.operationPayload());
        assertThat(operationPayload.get("operationParams").get("items").get(0).get("unitCost").decimalValue())
                .isEqualByComparingTo("120.00");
        assertThat(operationPayload.get("currentState").get("totalCost").decimalValue())
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void requestApprovalRejectsUnsupportedToolName() {
        assertThatThrownBy(() -> trustedActorContext.runAs(1L, "test-session", () -> approvalTool.requestApproval(
                "order_delete",
                "delete",
                Map.of("orderId", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported approval toolName: order_delete");
    }

    @Test
    void requestApprovalRejectsWrongPurchaseOrderOperationType() {
        assertThatThrownBy(() -> trustedActorContext.runAs(1L, "test-session", () -> approvalTool.requestApproval(
                "purchase_order_create",
                "update",
                Map.of("supplierId", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purchase_order_create operationType must be create");
    }

    @Test
    void requestApprovalRejectsDuplicatePurchaseOrderProduct() {
        Map<String, Object> operationParams = Map.of(
                "supplierId", 1,
                "items", List.of(
                        Map.of("productId", 2, "quantity", 10, "unitCost", new BigDecimal("120.00")),
                        Map.of("productId", 2, "quantity", 5, "unitCost", new BigDecimal("120.00"))));

        assertThatThrownBy(() -> trustedActorContext.runAs(1L, "test-session", () -> approvalTool.requestApproval(
                "purchase_order_create",
                "create",
                operationParams)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purchase order must not contain duplicate productId: 2");
    }

    private JsonNode json(String value) throws JacksonException {
        return objectMapper.readTree(value);
    }
}

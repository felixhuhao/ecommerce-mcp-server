package com.ecommerce.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ecommerce.agent.domain.ApprovalRecord;
import com.ecommerce.agent.mapper.ApprovalRecordMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.auth.service-token=test-service-token")
class McpHttpIdentityIntegrationTest {

    private static final Long TRUSTED_USER_ID = 77L;
    private static final String TRUSTED_SESSION_ID = "mcp-http-session";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<String> createdApprovalIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedApprovals() {
        createdApprovalIds.forEach(approvalId ->
                jdbcTemplate.update("DELETE FROM approval_record WHERE approval_id = ?", approvalId));
        jdbcTemplate.update("DELETE FROM approval_record WHERE session_id = ?", TRUSTED_SESSION_ID);
    }

    @Test
    void mcpToolCallBindsApprovalToTrustedActorFromHttpHeaders() throws Exception {
        String mcpSessionId = initializeMcpSession();
        sendInitializedNotification(mcpSessionId);

        Map<String, Object> operationParams = Map.of(
                "supplierId", 1,
                "items", List.of(Map.of(
                        "productId", 2,
                        "quantity", 10,
                        "unitCost", new BigDecimal("12.50"))));

        String responseBody = postJson(
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 2,
                          "method": "tools/call",
                          "params": {
                            "name": "request_approval",
                            "arguments": {
                              "toolName": "purchase_order_create",
                              "operationType": "create",
                              "operationParams": %s
                            }
                          }
                        }
                        """.formatted(objectMapper.writeValueAsString(operationParams)),
                mcpSessionId,
                200);

        JsonNode jsonRpcResponse = objectMapper.readTree(firstSseData(responseBody));
        JsonNode toolResult = jsonRpcResponse.get("result");
        assertThat(toolResult.get("isError").asBoolean()).isFalse();

        JsonNode approvalResponse = objectMapper.readTree(toolResult.get("content").get(0).get("text").asString());
        String approvalId = approvalResponse.get("approvalId").asString();
        createdApprovalIds.add(approvalId);

        ApprovalRecord approvalRecord = approvalRecordMapper.findById(approvalId);
        assertThat(approvalRecord.getUserId()).isEqualTo(TRUSTED_USER_ID);
        assertThat(approvalRecord.getSessionId()).isEqualTo(TRUSTED_SESSION_ID);
        assertThat(approvalRecord.getToolName()).isEqualTo("purchase_order_create");
        assertThat(approvalRecord.getStatus()).isEqualTo("pending");
    }

    @Test
    void mcpEndpointRejectsMissingServiceToken() throws Exception {
        HttpResponse<String> response = sendJson(
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {},
                            "clientInfo": {
                              "name": "mcp-http-identity-test",
                              "version": "0.0.1"
                            }
                          }
                        }
                        """,
                null,
                false);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void mcpToolListExcludesBackendExecutedWriteTools() throws Exception {
        String mcpSessionId = initializeMcpSession();
        sendInitializedNotification(mcpSessionId);

        String responseBody = postJson(
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 2,
                          "method": "tools/list"
                        }
                        """,
                mcpSessionId,
                200);

        JsonNode jsonRpcResponse = objectMapper.readTree(firstSseData(responseBody));
        List<String> toolNames = new ArrayList<>();
        jsonRpcResponse.get("result").get("tools").forEach(tool ->
                toolNames.add(tool.get("name").asString()));

        assertThat(toolNames).contains(
                "request_approval",
                "purchase_order_query",
                "order_query");
        assertThat(toolNames).doesNotContain(
                "purchase_order_create",
                "purchase_order_receive",
                "order_update");
    }

    private String initializeMcpSession() throws IOException, InterruptedException {
        HttpResponse<String> response = sendJson(
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {},
                            "clientInfo": {
                              "name": "mcp-http-identity-test",
                              "version": "0.0.1"
                            }
                          }
                        }
                        """,
                null,
                true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isPresent();
        return response.headers().firstValue("Mcp-Session-Id").orElseThrow();
    }

    private void sendInitializedNotification(String mcpSessionId) throws IOException, InterruptedException {
        postJson(
                """
                        {
                          "jsonrpc": "2.0",
                          "method": "notifications/initialized"
                        }
                        """,
                mcpSessionId,
                202);
    }

    private String postJson(String body, String mcpSessionId, int expectedStatus)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendJson(body, mcpSessionId, true);
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        return response.body();
    }

    private HttpResponse<String> sendJson(String body, String mcpSessionId, boolean includeServiceToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("X-User-Id", TRUSTED_USER_ID.toString())
                .header("X-Session-Id", TRUSTED_SESSION_ID)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (includeServiceToken) {
            request.header("X-Service-Token", "test-service-token");
        }

        if (mcpSessionId != null) {
            request.header("Mcp-Session-Id", mcpSessionId);
        }

        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String firstSseData(String responseBody) {
        return responseBody.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("SSE data line not found in response: " + responseBody));
    }
}

package com.ecommerce.agent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ecommerce.agent.auth.TrustedActor;
import com.ecommerce.agent.service.ApprovalExecutor;

@SpringBootTest(properties = "app.auth.service-token=test-service-token")
@AutoConfigureMockMvc
class ApprovalControllerInfrastructureErrorTest {

    private static final String SERVICE_TOKEN = "test-service-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovalExecutor approvalExecutor;

    @Test
    void executeReturnsRetryableServiceUnavailableForDatabaseErrors() throws Exception {
        String approvalId = "approval-with-db-error";
        when(approvalExecutor.execute(eq(approvalId), any(TrustedActor.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(post("/approvals/{approvalId}/execute", approvalId)
                .header("X-Service-Token", SERVICE_TOKEN)
                .header("X-User-Id", "1")
                .header("X-Session-Id", "test-session"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.approvalId").value(approvalId))
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.executionResult").doesNotExist())
                .andExpect(jsonPath("$.reasonCode").value("infrastructure_error"))
                .andExpect(jsonPath("$.retryable").value(true));
    }
}

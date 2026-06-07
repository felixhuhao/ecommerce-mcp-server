package com.ecommerce.agent.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class PurchaseOrder {
    private Long poId;
    private Long supplierId;
    private String status;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;
    private LocalDateTime receivedAt;
    private LocalDateTime cancelledAt;
}
